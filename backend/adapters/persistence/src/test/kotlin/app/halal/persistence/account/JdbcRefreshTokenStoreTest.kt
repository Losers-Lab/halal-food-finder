package app.halal.persistence.account

import app.halal.application.account.AccountRepository
import app.halal.application.account.RefreshTokenStore
import app.halal.application.account.StoredRefreshToken
import app.halal.domain.account.Account
import app.halal.domain.account.Email
import app.halal.domain.account.Role
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.string.shouldMatch
import org.flywaydb.core.Flyway
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Persistence adapter test for the rotating-hashed refresh-token store (sc-40):
 * proves the V2 migration creates the refresh_tokens table and that
 * [JdbcRefreshTokenStore] stores only a SHA-256 hash of each token, resolves it
 * back by hash, revokes it, and — for the sc-133 fix — atomically
 * consume-and-rotates in one transaction with an affected-row gate that makes
 * concurrent use of the same token exact-one-winner. Runs against a real PostGIS
 * container.
 */
class JdbcRefreshTokenStoreTest : FunSpec() {

    private val postgres: PostgreSQLContainer<*> = PostgreSQLContainer(
        DockerImageName.parse("postgis/postgis:17-3.4").asCompatibleSubstituteFor("postgres"),
    )
        .withDatabaseName("test")
        .withUsername("test")
        .withPassword("test")

    private lateinit var repository: AccountRepository
    private lateinit var refreshTokens: RefreshTokenStore
    private lateinit var jdbc: JdbcTemplate

    init {
        beforeSpec {
            postgres.start()
            Flyway.configure()
                .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
                .load()
                .migrate()

            val dataSource = DriverManagerDataSource().apply {
                setDriverClassName("org.postgresql.Driver")
                setUrl(postgres.jdbcUrl)
                setUsername(postgres.username)
                setPassword(postgres.password)
            }
            jdbc = JdbcTemplate(dataSource)
            repository = JdbcAccountRepository(jdbc)
            refreshTokens = JdbcRefreshTokenStore(
                jdbc,
                TransactionTemplate(DataSourceTransactionManager(dataSource)),
            )
        }
        afterSpec { postgres.stop() }

        test("V1+V2 migration creates the users and refresh_tokens tables") {
            val users = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_name = 'users'",
                Int::class.java,
            )
            val refresh = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_name = 'refresh_tokens'",
                Int::class.java,
            )
            users shouldBe 1
            refresh shouldBe 1
        }

        test("store persists only the SHA-256 hash, then findByToken resolves the account + expiry") {
            val account = repository.save(Account.new(Email("dave@example.com"), "argon2id\$stored-hash"))
            val expiresAt = Instant.now().plus(30, ChronoUnit.DAYS)

            refreshTokens.store("opaque-refresh-token", account.id, expiresAt)

            val found = refreshTokens.findByToken("opaque-refresh-token")
            found shouldNotBe null
            found!!.accountId shouldBe account.id
            // Postgres stores TIMESTAMPTZ at microsecond precision; compare the
            // round-tripped expiry within a microsecond, not bit-for-bit.
            val precisionDiff = Duration.between(found.expiresAt, expiresAt).abs().toNanos()
            (precisionDiff < 1000L) shouldBe true

            // The persisted column is a 64-char SHA-256 hex of the token, never the token itself.
            val storedHash = jdbc.queryForObject(
                "SELECT token_hash FROM refresh_tokens WHERE token_hash = ?",
                String::class.java,
                sha256("opaque-refresh-token"),
            )
            storedHash shouldBe sha256("opaque-refresh-token")
            storedHash shouldNotBe "opaque-refresh-token"
            storedHash shouldMatch "[0-9a-f]{64}"
        }

        test("findByToken returns null for an unknown refresh token") {
            refreshTokens.findByToken("never-issued-token").shouldBeNull()
        }

        test("revoke invalidates the token so it can no longer resolve") {
            val account = repository.save(Account.new(Email("erin@example.com"), "argon2id\$stored-hash"))

            refreshTokens.store("to-revoke", account.id, Instant.now().plus(10, ChronoUnit.DAYS))
            refreshTokens.findByToken("to-revoke").shouldNotBe(null)

            refreshTokens.revoke("to-revoke")

            refreshTokens.findByToken("to-revoke").shouldBeNull()
        }

        test("consumeAndRotate deletes the old token and stores the new one in one call") {
            val account = repository.save(Account.new(Email("grace@example.com"), "argon2id\$stored-hash"))
            refreshTokens.store("token-old", account.id, Instant.now().plus(10, ChronoUnit.DAYS))

            val rotated = refreshTokens.consumeAndRotate(
                presentedToken = "token-old",
                newToken = "token-new",
                accountId = account.id,
                expiresAt = Instant.now().plus(20, ChronoUnit.DAYS),
            )

            rotated shouldBe true
            refreshTokens.findByToken("token-old").shouldBeNull()
            refreshTokens.findByToken("token-new")!!.accountId shouldBe account.id
        }

        test("consumeAndRotate returns false and inserts nothing when the presented token is already gone") {
            val account = repository.save(Account.new(Email("hans@example.com"), "argon2id\$stored-hash"))

            val rotated = refreshTokens.consumeAndRotate(
                presentedToken = "never-issued",
                newToken = "should-not-appear",
                accountId = account.id,
                expiresAt = Instant.now().plus(20, ChronoUnit.DAYS),
            )

            rotated shouldBe false
            refreshTokens.findByToken("should-not-appear").shouldBeNull()
        }

        test("concurrent consumeAndRotate of the same token is exact-one-winner: one true, one false, one live row (sc-133 fix)") {
            val account = repository.save(Account.new(Email("ira@example.com"), "argon2id\$stored-hash"))
            refreshTokens.store("contended", account.id, Instant.now().plus(10, ChronoUnit.DAYS))

            // Two threads race to consume-and-rotate the SAME live token into two
            // different replacements. Exactly one may win the gated delete.
            val start = CyclicBarrier(2)
            val pool = Executors.newFixedThreadPool(2)
            val results = try {
                val futures = (1..2).map { i ->
                    pool.submit(Callable {
                        start.await()
                        refreshTokens.consumeAndRotate(
                            presentedToken = "contended",
                            newToken = "replacement-$i",
                            accountId = account.id,
                            expiresAt = Instant.now().plus(20, ChronoUnit.DAYS),
                        )
                    })
                }
                futures.map { it.get(30, TimeUnit.SECONDS) }
            } finally {
                pool.shutdownNow()
            }

            results.count { it } shouldBe 1

            // Exactly one live row survives for this account (the winner's token).
            val live = jdbc.queryForList(
                "SELECT token_hash FROM refresh_tokens WHERE account_id = ?",
                account.id,
            )
            live.size shouldBe 1
        }

        test("tokens for the same account can coexist (one per active session)") {
            val account = repository.save(Account.new(Email("fay@example.com"), "argon2id\$stored-hash"))

            refreshTokens.store("token-a", account.id, Instant.now().plus(10, ChronoUnit.DAYS))
            refreshTokens.store("token-b", account.id, Instant.now().plus(10, ChronoUnit.DAYS))

            refreshTokens.findByToken("token-a").shouldNotBe(null)
            refreshTokens.findByToken("token-b").shouldNotBe(null)
        }

        test("findById round-trips a persisted account by its database id") {
            val saved = repository.save(Account.fromStorage(
                id = UUID.randomUUID(),
                email = Email("owner@example.com"),
                passwordHash = "argon2id\$owner-hash",
                role = Role.VERIFICATION_COMMITTEE,
            ))

            val found = repository.findById(saved.id)
            found shouldNotBe null
            found!!.id shouldBe saved.id
            found.email.value shouldBe "owner@example.com"
            found.role shouldBe Role.VERIFICATION_COMMITTEE
        }
    }

    private fun sha256(input: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
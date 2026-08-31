package com.tahirslist.persistence.account

import com.tahirslist.application.account.AccountRepository
import com.tahirslist.application.account.RefreshTokenStore
import com.tahirslist.application.account.StoredRefreshToken
import com.tahirslist.domain.account.Account
import com.tahirslist.domain.account.Email
import com.tahirslist.domain.account.Role
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

/**
 * Persistence adapter test for the rotating-hashed refresh-token store (sc-40,
 * hardened for sc-136). Proves the V2+V3 migrations create the necessary tables
 * and columns, that [JdbcRefreshTokenStore] stores only a SHA-256 hash of each
 * token, resolves live vs consumed records, keeps rotation within a token family,
 * and supports whole-family revocation on reuse.
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

        test("V1+V2+V3 migrations create the users and refresh_tokens tables with family columns") {
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

            val familyCol = jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'refresh_tokens' AND column_name = 'family_id'",
            )
            val consumedCol = jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'refresh_tokens' AND column_name = 'consumed_at'",
            )
            familyCol.size shouldBe 1
            consumedCol.size shouldBe 1
        }

        test("store persists a live token in the given family; findByToken resolves account, family, and expiry") {
            val account = repository.save(Account.new(Email("dave@example.com"), "argon2id\$stored-hash"))
            val family = UUID.randomUUID()
            val expiresAt = Instant.now().plus(30, ChronoUnit.DAYS)

            refreshTokens.store("opaque-refresh-token", account.id, family, expiresAt)

            val found = refreshTokens.findByToken("opaque-refresh-token")
            found shouldNotBe null
            found!!.accountId shouldBe account.id
            found.familyId shouldBe family
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

        test("findByToken returns null for an unknown token and for a consumed token") {
            refreshTokens.findByToken("never-issued-token").shouldBeNull()

            val account = repository.save(Account.new(Email("bob@example.com"), "argon2id\$stored-hash"))
            val family = UUID.randomUUID()
            refreshTokens.store("live-token", account.id, family, Instant.now().plus(10, ChronoUnit.DAYS))
            val live = refreshTokens.findByToken("live-token")
            live!!.familyId shouldBe family
            // After signing it out, it is no longer live.
            refreshTokens.revoke("live-token")
            refreshTokens.findByToken("live-token").shouldBeNull()
        }

        test("revokeFamily removes every token in the family (live and consumed)") {
            val account = repository.save(Account.new(Email("carl@example.com"), "argon2id\$stored-hash"))
            val family = UUID.randomUUID()
            refreshTokens.store("live-1", account.id, family, Instant.now().plus(10, ChronoUnit.DAYS))
            refreshTokens.store("live-2", account.id, family, Instant.now().plus(10, ChronoUnit.DAYS))

            refreshTokens.revokeFamily(family)

            refreshTokens.findByToken("live-1").shouldBeNull()
            refreshTokens.findByToken("live-2").shouldBeNull()
        }

        test("revokeFamily leaves a different family's tokens untouched") {
            val account = repository.save(Account.new(Email("carol@example.com"), "argon2id\$stored-hash"))
            val doomed = UUID.randomUUID()
            val unrelated = UUID.randomUUID()
            refreshTokens.store("doomed-token", account.id, doomed, Instant.now().plus(10, ChronoUnit.DAYS))
            refreshTokens.store("safe-token", account.id, unrelated, Instant.now().plus(10, ChronoUnit.DAYS))

            refreshTokens.revokeFamily(doomed)

            refreshTokens.findByToken("doomed-token").shouldBeNull()
            refreshTokens.findByToken("safe-token").shouldNotBe(null)
        }

        test("consumeAndRotate keeps the new token in the same family and records the old one as consumed") {
            val account = repository.save(Account.new(Email("erin@example.com"), "argon2id\$stored-hash"))
            val family = UUID.randomUUID()
            refreshTokens.store("first-token", account.id, family, Instant.now().plus(10, ChronoUnit.DAYS))

            val rotated = refreshTokens.consumeAndRotate(
                presentedToken = "first-token",
                newToken = "second-token",
                accountId = account.id,
                familyId = family,
                expiresAt = Instant.now().plus(10, ChronoUnit.DAYS),
            )

            rotated shouldBe true
            // The old token is no longer live, but a consumed record exists.
            refreshTokens.findByToken("first-token").shouldBeNull()
            val consumed = refreshTokens.findConsumedByToken("first-token")
            consumed shouldNotBe null
            consumed!!.familyId shouldBe family
            // The new token lives in the SAME family.
            val live = refreshTokens.findByToken("second-token")
            live shouldNotBe null
            live!!.familyId shouldBe family
        }

        test("consumeAndRotate returns false when the presented token is already consumed (reuse race loser)") {
            val account = repository.save(Account.new(Email("frank@example.com"), "argon2id\$stored-hash"))
            val family = UUID.randomUUID()
            refreshTokens.store("only-token", account.id, family, Instant.now().plus(10, ChronoUnit.DAYS))

            // First caller wins the race: consumes "only-token" and mints its child.
            refreshTokens.consumeAndRotate("only-token", "winner-token", account.id, family, Instant.now().plus(10, ChronoUnit.DAYS))
            // Second caller loses: presents the now-consumed token.
            val second = refreshTokens.consumeAndRotate("only-token", "loser-token-2", account.id, family, Instant.now().plus(10, ChronoUnit.DAYS))

            second shouldBe false
            // The winner's replacement is live; the old token is a consumed record.
            refreshTokens.findByToken("winner-token").shouldNotBe(null)
            refreshTokens.findByToken("only-token").shouldBeNull()
            refreshTokens.findConsumedByToken("only-token").shouldNotBe(null)
            // The losing caller must not mint a live record.
            refreshTokens.findByToken("loser-token-2").shouldBeNull()
        }

        test("consumeAndRotate returns false for an unknown token without inserting anything") {
            val account = repository.save(Account.new(Email("gina@example.com"), "argon2id\$stored-hash"))
            val other = refreshTokens.consumeAndRotate(
                "never-issued", "wont-land", account.id, UUID.randomUUID(), Instant.now().plus(10, ChronoUnit.DAYS),
            )
            other shouldBe false
            refreshTokens.findByToken("wont-land").shouldBeNull()
        }

        test("revoke invalidates the token so it can no longer resolve as live") {
            val account = repository.save(Account.new(Email("hani@example.com"), "argon2id\$stored-hash"))
            refreshTokens.store("to-revoke", account.id, UUID.randomUUID(), Instant.now().plus(10, ChronoUnit.DAYS))
            refreshTokens.findByToken("to-revoke").shouldNotBe(null)

            refreshTokens.revoke("to-revoke")

            refreshTokens.findByToken("to-revoke").shouldBeNull()
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
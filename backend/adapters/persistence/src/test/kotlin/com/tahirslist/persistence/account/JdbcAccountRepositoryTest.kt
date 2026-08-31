package com.tahirslist.persistence.account

import com.tahirslist.application.account.AccountRepository
import com.tahirslist.application.account.EmailAlreadyExistsException
import com.tahirslist.domain.account.Account
import com.tahirslist.domain.account.Email
import com.tahirslist.domain.account.Role
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.flywaydb.core.Flyway
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * Persistence adapter test: proves the Flyway migration creates the users table
 * and that [JdbcAccountRepository] round-trips an account via the
 * application-layer [AccountRepository] port against a real PostGIS container.
 */
class JdbcAccountRepositoryTest : FunSpec() {

    private val postgres: PostgreSQLContainer<*> = PostgreSQLContainer(
        DockerImageName.parse("postgis/postgis:17-3.4").asCompatibleSubstituteFor("postgres"),
    )
        .withDatabaseName("test")
        .withUsername("test")
        .withPassword("test")

    private lateinit var repository: AccountRepository

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
            repository = JdbcAccountRepository(JdbcTemplate(dataSource))
        }
        afterSpec { postgres.stop() }

        test("V1 migration creates the users table") {
            val dataSource = DriverManagerDataSource().apply {
                setDriverClassName("org.postgresql.Driver")
                setUrl(postgres.jdbcUrl)
                setUsername(postgres.username)
                setPassword(postgres.password)
            }
            val count = JdbcTemplate(dataSource)
                .queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_name = 'users'", Int::class.java)
            count shouldBe 1
        }

        test("saved account is found back by its canonical email") {
            val account = Account.new(email = Email("User@Example.COM"), passwordHash = "argon2id\$stored-hash")
            val saved = repository.save(account)

            saved.email.value shouldBe "user@example.com"
            saved.passwordHash shouldBe "argon2id\$stored-hash"
            saved.role shouldBe Role.USER
            saved.id shouldNotBe account.id // DB generates the id, not the caller
        }

        test("findByEmail returns null for an unknown email") {
            repository.findByEmail(Email("nobody@example.com")) shouldBe null
        }

        test("persisted account restores the exact role and hash") {
            val account = Account.fromStorage(
                id = java.util.UUID.randomUUID(),
                email = Email("owner@example.com"),
                passwordHash = "argon2id\$owner-hash",
                role = Role.RESTAURANT_OWNER,
            )
            repository.save(account)

            val found = repository.findByEmail(Email("OWNER@example.com"))
            found shouldNotBe null
            found!!.role shouldBe Role.RESTAURANT_OWNER
            found.passwordHash shouldBe "argon2id\$owner-hash"
        }

        test("V1 migration enforces a unique constraint on users.email") {
            val dataSource = DriverManagerDataSource().apply {
                setDriverClassName("org.postgresql.Driver")
                setUrl(postgres.jdbcUrl)
                setUsername(postgres.username)
                setPassword(postgres.password)
            }
            val uniqueIndexCount = JdbcTemplate(dataSource).queryForObject(
                """
                SELECT count(*)
                FROM pg_index
                WHERE indisunique = true
                  AND indisprimary = false
                  AND indrelid = 'users'::regclass
                """.trimIndent(),
                Int::class.java,
            )
            uniqueIndexCount shouldBe 1
        }

        test("a second save of the same email surfaces EmailAlreadyExistsException (sc-135 Gap 6: unique-constraint race backstop, not a 500)") {
            val first = Account.new(email = Email("race@example.com"), passwordHash = "argon2id\$one")
            repository.save(first)

            // Simulate the lost TOCTOU race: a concurrent signup that already passed
            // the application-layer findByEmail check now hits the DB unique constraint.
            val racer = Account.new(email = Email("race@example.com"), passwordHash = "argon2id\$two")
            val ex = shouldThrow<EmailAlreadyExistsException> { repository.save(racer) }

            ex.email.value shouldBe "race@example.com"

            // Exactly one row survives; the unique constraint is the backstop.
            repository.findByEmail(Email("race@example.com")) shouldNotBe null
            repository.findByEmail(Email("race@example.com"))!!.passwordHash shouldBe "argon2id\$one"
        }
    }
}
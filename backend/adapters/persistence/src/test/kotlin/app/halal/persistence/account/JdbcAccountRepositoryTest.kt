package app.halal.persistence.account

import app.halal.application.account.AccountRepository
import app.halal.domain.account.Account
import app.halal.domain.account.Email
import app.halal.domain.account.Role
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
    }
}
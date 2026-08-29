package app.halal.bootstrap

import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringTestExtension
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.test.context.ContextConfiguration
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * Base class for full-context, database-backed boot tests. Starts a single
 * shared PostGIS container for the whole JVM (a companion object so all tests
 * in one run reuse it) and points Spring's datasource + Flyway at it.
 *
 * The application persists accounts via [app.halal.persistence.account.JdbcAccountRepository],
 * so any context that boots the full application graph requires a DataSource.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = [PostgresBootTest.Initializer::class])
abstract class PostgresBootTest : FunSpec() {

    override fun extensions() = listOf(SpringTestExtension())

    protected companion object {
        val postgres: PostgreSQLContainer<*> by lazy {
            PostgreSQLContainer(
                DockerImageName.parse("postgis/postgis:17-3.4").asCompatibleSubstituteFor("postgres"),
            )
                .withDatabaseName("test")
                .withUsername("test")
                .withPassword("test")
                .apply { start() }
        }
    }

    /**
     * Registers the Testcontainers datasource before the context boots so
     * Spring's DataSource + Flyway auto-configuration migrate the users table.
     */
    @TestConfiguration
    class Initializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
        override fun initialize(context: ConfigurableApplicationContext) {
            TestPropertyValues.of(
                "spring.datasource.url=${postgres.jdbcUrl}",
                "spring.datasource.username=${postgres.username}",
                "spring.datasource.password=${postgres.password}",
            ).applyTo(context.environment)
        }
    }
}
package app.halal.persistence

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import org.flywaydb.core.Flyway
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.DriverManager

/**
 * TDD harness smoke test. Proves that a real PostGIS container can be started
 * via Testcontainers, reached over JDBC, queried with PostGIS spatial functions,
 * and migrated by Flyway — the exact tooling every persistence test will rely on.
 */
class PostgisSmokeTest : FunSpec() {

    private val postgis: PostgreSQLContainer<*> = PostgreSQLContainer(
        // postgis/postgis extends the official postgres image; declare it compatible
        // so Testcontainers accepts it for PostgreSQLContainer.
        DockerImageName.parse("postgis/postgis:17-3.4").asCompatibleSubstituteFor("postgres"),
    )
        .withDatabaseName("test")
        .withUsername("test")
        .withPassword("test")

    init {
        beforeSpec { postgis.start() }
        afterSpec { postgis.stop() }

        test("connects to PostGIS over JDBC and can run a spatial query") {
            postgis.isRunning shouldBe true

            DriverManager.getConnection(postgis.jdbcUrl, postgis.username, postgis.password).use { conn ->
                conn.createStatement().use { st ->
                    // idempotent — exercises the PostGIS extension regardless of
                    // whether the image pre-enabled it in the test database.
                    st.execute("CREATE EXTENSION IF NOT EXISTS postgis")
                    st.executeQuery("SELECT postgis_version()").use { rs ->
                        rs.next()
                        rs.getString(1).shouldNotBeBlank()
                    }
                }
            }
        }

        test("Flyway connects and runs against the live PostGIS database") {
            val flyway = Flyway.configure()
                .dataSource(postgis.jdbcUrl, postgis.username, postgis.password)
                .load()

            val result = flyway.migrate()

            // The users (V1) and refresh_tokens (V2) migrations run cleanly on a real PostGIS DB.
            result.migrationsExecuted shouldBe 2
        }
    }
}
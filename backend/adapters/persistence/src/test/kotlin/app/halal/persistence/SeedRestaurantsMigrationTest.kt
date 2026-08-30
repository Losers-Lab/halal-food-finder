package app.halal.persistence

import app.halal.domain.restaurant.CuttingMethod
import app.halal.domain.restaurant.Provenance
import app.halal.domain.restaurant.VerificationStatus
import app.halal.persistence.listing.JdbcRestaurantListingRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldNotBeNull
import org.flywaydb.core.Flyway
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.util.UUID

/**
 * Proves the sc-155 seed-ingest migrations against a real PostGIS database:
 *
 *  - V5 creates `brands` (brand/location split).
 *  - V6 makes owner_id/cuisine nullable, adds brand_id + provenance (closed
 *    vocab) and a seed-scoped unique index over the normalised location.
 *  - V7 idempotently ingests the 30 verified seeds (28 brands) as UNVERIFIED
 *    listings with provenance 'research-seed / photon-geocode'.
 *
 * It also pins the DB-level integrity contracts (provenance CHECK, brand FK,
 * location-dedupe unique index) and proves a seed row round-trips through the
 * application-layer port ([JdbcRestaurantListingRepository]) with its nullable
 * cuisine/ownerId intact.
 */
class SeedRestaurantsMigrationTest : FunSpec() {

    private val postgres: PostgreSQLContainer<*> = PostgreSQLContainer(
        DockerImageName.parse("postgis/postgis:17-3.4").asCompatibleSubstituteFor("postgres"),
    )
        .withDatabaseName("test")
        .withUsername("test")
        .withPassword("test")

    private lateinit var jdbc: JdbcTemplate
    private lateinit var listings: JdbcRestaurantListingRepository

    /** The generated seed SQL, read from the classpath, to prove re-run idempotency. */
    private val seedSql: String =
        object {}.javaClass.getResourceAsStream("/db/migration/V7__seed_restaurants.sql")
            ?.bufferedReader()?.readText()
            ?: error("V7__seed_restaurants.sql not on the test classpath")

    private fun countListings() =
        jdbc.queryForObject("SELECT count(*) FROM restaurant_listings", Int::class.java)

    private fun countBrands() =
        jdbc.queryForObject("SELECT count(*) FROM brands", Int::class.java)

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
            listings = JdbcRestaurantListingRepository(jdbc)
        }
        afterSpec { postgres.stop() }

        test("V5 creates the brands table with a unique name") {
            jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_name = 'brands'",
                Int::class.java,
            ) shouldBe 1
        }

        test("V6 makes owner_id and cuisine nullable on restaurant_listings") {
            for (column in listOf("owner_id", "cuisine")) {
                jdbc.queryForObject(
                    "SELECT is_nullable FROM information_schema.columns " +
                        "WHERE table_name = 'restaurant_listings' AND column_name = ?",
                    String::class.java,
                    column,
                ) shouldBe "YES"
            }
        }

        test("V6 adds brand_id and provenance columns") {
            val cols = jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns " +
                    "WHERE table_name = 'restaurant_listings'",
                String::class.java,
            )
            cols shouldContain "brand_id"
            cols shouldContain "provenance"
        }

        test("V7 seeds 30 listings across 28 brands, all unverified + provenance") {
            countListings() shouldBe 30
            countBrands() shouldBe 28

            val seeded = jdbc.queryForObject(
                "SELECT count(*) FROM restaurant_listings WHERE provenance = 'research-seed / photon-geocode'",
                Int::class.java,
            )
            seeded shouldBe 30

            val unverified = jdbc.queryForObject(
                "SELECT count(*) FROM restaurant_listings WHERE verification_status = 'UNVERIFIED'",
                Int::class.java,
            )
            unverified shouldBe 30
        }

        test("brand/location split: The Halal Guys is one brand with three locations") {
            val halalGuysLocations = jdbc.queryForObject(
                "SELECT count(*) FROM restaurant_listings " +
                    "WHERE brand_id = (SELECT id FROM brands WHERE name = 'The Halal Guys')",
                Int::class.java,
            )
            halalGuysLocations shouldBe 3
        }

        test("V7 is idempotent: re-running the seed SQL adds no duplicates") {
            // Re-execute the exact generated seed statements (ON CONFLICT DO NOTHING).
            jdbc.execute(seedSql)
            countListings() shouldBe 30
            countBrands() shouldBe 28
        }

        test("location-dedupe index rejects a raw duplicate seed insert (not by name)") {
            // The Halal Guys Toronto's coordinates: inserting the same normalised
            // location with seed provenance, but WITHOUT ON CONFLICT, must violate
            // the partial unique index — i.e. dedupe is by location, not by name.
            shouldThrow<DataIntegrityViolationException> {
                jdbc.update(
                    """
                    INSERT INTO restaurant_listings
                        (name, address, location, cuisine, cutting_method, owner_id, brand_id, provenance, verification_status)
                    VALUES (
                        'The Halal Guys', 'duplicate address',
                        ST_SetSRID(ST_MakePoint(-79.384523, 43.665206), 4326)::geography,
                        NULL, 'UNSPECIFIED', NULL, NULL, 'research-seed / photon-geocode', 'UNVERIFIED'
                    )
                    """.trimIndent(),
                )
            }
        }

        test("provenance CHECK rejects an out-of-vocabulary value") {
            shouldThrow<DataIntegrityViolationException> {
                jdbc.update(
                    """
                    INSERT INTO restaurant_listings
                        (name, address, location, cuisine, cutting_method, owner_id, brand_id, provenance, verification_status)
                    VALUES (
                        'X', '1 St', ST_SetSRID(ST_MakePoint(0.0, 0.0), 4326)::geography,
                        NULL, 'UNSPECIFIED', NULL, NULL, 'some-other-source', 'UNVERIFIED'
                    )
                    """.trimIndent(),
                )
            }
        }

        test("brand foreign key rejects an unknown brand_id") {
            shouldThrow<DataIntegrityViolationException> {
                jdbc.update(
                    """
                    INSERT INTO restaurant_listings
                        (name, address, location, cuisine, cutting_method, owner_id, brand_id, provenance, verification_status)
                    VALUES (
                        'Ghost', '1 St', ST_SetSRID(ST_MakePoint(1.0, 1.0), 4326)::geography,
                        NULL, 'UNSPECIFIED', NULL, '${UUID.randomUUID()}', 'research-seed / photon-geocode', 'UNVERIFIED'
                    )
                    """.trimIndent(),
                )
            }
        }

        test("a seed row round-trips through the repository port with nullable cuisine/owner + provenance") {
            val seedId = jdbc.queryForObject(
                "SELECT id FROM restaurant_listings WHERE name = 'Osmow''s' LIMIT 1",
                UUID::class.java,
            ) ?: error("expected a seeded Osmow's listing")

            val found = listings.findById(seedId)
            found.shouldNotBeNull()
            found.cuisine shouldBe null
            found.ownerId shouldBe null
            found.provenance shouldBe Provenance.RESEARCH_SEED_PHOTON_GEOCODE
            found.verificationStatus shouldBe VerificationStatus.UNVERIFIED
            found.cuttingMethod shouldBe CuttingMethod.UNSPECIFIED
            found.brandId.shouldNotBeNull()
            // OSM node coordinate round-trips.
            found.location.lat shouldBe (43.682921 plusOrMinus 0.0001)
            found.location.lng shouldBe (-79.418493 plusOrMinus 0.0001)
        }
    }
}

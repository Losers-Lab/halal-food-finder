package app.halal.persistence.listing

import app.halal.domain.account.Account
import app.halal.domain.account.Email
import app.halal.domain.restaurant.Cuisine
import app.halal.domain.restaurant.CuttingMethod
import app.halal.domain.restaurant.LatLng
import app.halal.domain.restaurant.RestaurantListing
import app.halal.domain.restaurant.VerificationStatus
import app.halal.persistence.account.JdbcAccountRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.flywaydb.core.Flyway
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.util.UUID

/**
 * Persistence adapter test: proves V4 migration creates the restaurant_listings
 * table (PostGIS geography(Point,4326)) and that [JdbcRestaurantListingRepository]
 * round-trips a listing through the application-layer port against a real
 * PostGIS container. Also pins the migration's integrity contracts (CHECK/FK/NOT
 * NULL/boundary) at the DB layer, where they are the real backstop — a later
 * verification story must deliberately widen the UNVERIFIED-only contract.
 */
class JdbcRestaurantListingRepositoryTest : FunSpec() {

    private val postgres: PostgreSQLContainer<*> = PostgreSQLContainer(
        DockerImageName.parse("postgis/postgis:17-3.4").asCompatibleSubstituteFor("postgres"),
    )
        .withDatabaseName("test")
        .withUsername("test")
        .withPassword("test")

    private lateinit var jdbc: JdbcTemplate
    private lateinit var listings: JdbcRestaurantListingRepository
    private lateinit var accounts: JdbcAccountRepository

    /** A fresh owner so FK-dependent inserts always have a valid target (unique email). */
    private fun newOwner(): UUID =
        accounts.save(Account.new(email = Email("owner-${UUID.randomUUID()}@example.com"), passwordHash = "argon2id\$h")).id

    /** Raw INSERT with per-column overrides; pass null/expression to force a violation. */
    private fun tryInsert(
        owner: UUID?,
        name: String? = "Halal Grill",
        address: String? = "123 Main St",
        locationExpr: String = "ST_SetSRID(ST_MakePoint(-74.006, 40.7128), 4326)::geography",
        cuisine: String? = "mediterranean",
        cuttingMethod: String? = "HAND_CUT",
        verificationStatus: String? = "UNVERIFIED",
    ): Int = jdbc.update(
        """
        INSERT INTO restaurant_listings (name, address, location, cuisine, cutting_method, owner_id, verification_status)
        VALUES (?, ?, $locationExpr, ?, ?, ?, ?)
        """.trimIndent(),
        name, address, cuisine, cuttingMethod, owner, verificationStatus,
    )

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
            accounts = JdbcAccountRepository(jdbc)
        }
        afterSpec { postgres.stop() }

        test("V4 migration creates the restaurant_listings table") {
            val count = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_name = 'restaurant_listings'",
                Int::class.java,
            )
            count shouldBe 1
        }

        test("migration stores location as PostGIS geography(Point, 4326)") {
            val row = jdbc.queryForMap(
                "SELECT f_geography_column AS col, type, srid FROM geography_columns " +
                    "WHERE f_table_name = 'restaurant_listings' AND f_geography_column = 'location'",
            )
            row["col"] shouldBe "location"
            row["type"] shouldBe "Point"
            row["srid"] shouldBe 4326
        }

        test("round-trips a listing and its PostGIS point") {
            val owner = accounts.save(Account.new(email = Email("owner@example.com"), passwordHash = "argon2id\$h"))
            val listing = RestaurantListing.new(
                name = "Halal Grill",
                address = "123 Main St",
                location = LatLng(40.7128, -74.0060),
                cuisine = Cuisine("Mediterranean"),
                cuttingMethod = CuttingMethod.HAND_CUT,
                ownerId = owner.id,
            )

            val saved = listings.save(listing)
            saved.id shouldNotBe listing.id // DB generates the id
            saved.verificationStatus shouldBe VerificationStatus.UNVERIFIED

            val found = listings.findById(saved.id)
            found shouldNotBe null
            found!!.name shouldBe "Halal Grill"
            found.address shouldBe "123 Main St"
            found.ownerId shouldBe owner.id
            found.cuisine.value shouldBe "mediterranean"
            found.cuttingMethod shouldBe CuttingMethod.HAND_CUT
            found.verificationStatus shouldBe VerificationStatus.UNVERIFIED
            found.location.lat shouldBe (40.7128 plusOrMinus 0.0001)
            found.location.lng shouldBe (-74.0060 plusOrMinus 0.0001)
        }

        test("findById returns null for an unknown id") {
            listings.findById(UUID.randomUUID()) shouldBe null
        }

        test("migration enforces the verification_status contract (only UNVERIFIED)") {
            // Default insert is allowed (listing-first model).
            tryInsert(owner = newOwner())
            // A 'VERIFIED' status is not legal yet — the CHECK must reject it.
            shouldThrow<DataIntegrityViolationException> {
                tryInsert(owner = newOwner(), verificationStatus = "VERIFIED")
            }
        }

        test("migration enforces cutting_method values") {
            tryInsert(owner = newOwner(), cuttingMethod = "MACHINE_CUT")
            tryInsert(owner = newOwner(), cuttingMethod = "UNSPECIFIED")
            shouldThrow<DataIntegrityViolationException> {
                tryInsert(owner = newOwner(), cuttingMethod = "FOO")
            }
        }

        test("migration enforces the owner_id foreign key (DB-level backstop)") {
            // Even if the use case's owner check passed, a now-deleted/unrelated
            // owner must still be rejected by the FK — never a silently orphaned row.
            shouldThrow<DataIntegrityViolationException> {
                tryInsert(owner = UUID.randomUUID())
            }
        }

        test("migration enforces NOT NULL on every required column") {
            val owner = newOwner()
            shouldThrow<DataIntegrityViolationException> { tryInsert(owner = owner, name = null) }
            shouldThrow<DataIntegrityViolationException> { tryInsert(owner = owner, address = null) }
            shouldThrow<DataIntegrityViolationException> {
                tryInsert(owner = owner, locationExpr = "NULL")
            }
            shouldThrow<DataIntegrityViolationException> { tryInsert(owner = owner, cuisine = null) }
            shouldThrow<DataIntegrityViolationException> { tryInsert(owner = owner, cuttingMethod = null) }
            shouldThrow<DataIntegrityViolationException> { tryInsert(owner = null) }
        }

        test("migration enforces the cuisine VARCHAR(64) boundary") {
            tryInsert(owner = newOwner(), cuisine = "c".repeat(64))
            shouldThrow<DataIntegrityViolationException> {
                tryInsert(owner = newOwner(), cuisine = "c".repeat(65))
            }
        }
    }
}

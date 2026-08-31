package com.tahirslist.persistence.listing

import com.tahirslist.domain.account.Account
import com.tahirslist.domain.account.Email
import com.tahirslist.domain.restaurant.Cuisine
import com.tahirslist.domain.restaurant.CuttingMethod
import com.tahirslist.domain.restaurant.LatLng
import com.tahirslist.domain.restaurant.Price
import com.tahirslist.domain.restaurant.Rating
import com.tahirslist.domain.restaurant.RestaurantListing
import com.tahirslist.domain.restaurant.VerificationStatus
import com.tahirslist.persistence.account.JdbcAccountRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.flywaydb.core.Flyway
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.math.BigDecimal
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
            listings = JdbcRestaurantListingRepository(
                jdbc,
                TransactionTemplate(DataSourceTransactionManager(dataSource)),
            )
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
            found.cuisine!!.value shouldBe "mediterranean"
            found.cuttingMethod shouldBe CuttingMethod.HAND_CUT
            found.verificationStatus shouldBe VerificationStatus.UNVERIFIED
            found.brandId shouldBe null
            found.provenance shouldBe null
            found.location.lat shouldBe (40.7128 plusOrMinus 0.0001)
            found.location.lng shouldBe (-74.0060 plusOrMinus 0.0001)
        }

        test("findById returns null for an unknown id") {
            listings.findById(UUID.randomUUID()) shouldBe null
        }

        test("save mirrors the listing into the listing_search read model atomically (sc-10)") {
            val owner = accounts.save(Account.new(email = Email("mirror@example.com"), passwordHash = "argon2id\$h"))
            val listing = RestaurantListing.new(
                name = "Mirror Test Grill",
                address = "9 Search Rd",
                location = LatLng(43.7, -79.4),
                cuisine = Cuisine("Halal"),
                cuttingMethod = CuttingMethod.HAND_CUT,
                ownerId = owner.id,
            )

            val saved = listings.save(listing)

            // The search projection must carry the same row, immediately searchable.
            val mirrored = jdbc.queryForMap(
                "SELECT name, address, verification_status FROM listing_search WHERE id = ?",
                saved.id,
            )
            mirrored["name"] shouldBe "Mirror Test Grill"
            mirrored["address"] shouldBe "9 Search Rd"
            mirrored["verification_status"] shouldBe "UNVERIFIED"
        }

        test("save persists price and mirrors it plus the cuisine into the search/store (sc-43/44)") {
            val owner = accounts.save(Account.new(email = Email("price-${UUID.randomUUID()}@example.com"), passwordHash = "argon2id\$h"))
            val listing = RestaurantListing.new(
                name = "Price Grill",
                address = "7 Dine Dr",
                location = LatLng(43.7, -79.4),
                cuisine = Cuisine("Halal"),
                cuttingMethod = CuttingMethod.HAND_CUT,
                ownerId = owner.id,
                price = Price(BigDecimal("15.50")),
            )

            val saved = listings.save(listing)
            saved.price?.value shouldBe BigDecimal("15.50")

            // Price round-trips through the source read path.
            val found = listings.findById(saved.id)!!
            found.price?.value shouldBe BigDecimal("15.50")

            // The multi-cuisine store gets the primary cuisine (sc-44) so a
            // user-added listing is immediately cuisine-filterable.
            val cuisineRows = jdbc.queryForList(
                "SELECT cuisine FROM restaurant_listing_cuisines WHERE listing_id = ?",
                saved.id,
            )
            cuisineRows.map { it["cuisine"] } shouldBe listOf("halal")

            // The search projection mirrors the price (sc-43 reads listing_search only).
            val mirroredPrice = jdbc.queryForObject(
                "SELECT price FROM listing_search WHERE id = ?",
                BigDecimal::class.java,
                saved.id,
            )
            mirroredPrice shouldBe BigDecimal("15.50")
        }

        test("save persists rating and mirrors it into the search projection (sc-45)") {
            val owner = accounts.save(Account.new(email = Email("rated-${UUID.randomUUID()}@example.com"), passwordHash = "argon2id\$h"))
            val listing = RestaurantListing.new(
                name = "Rated Grill",
                address = "5 Stars Ave",
                location = LatLng(43.7, -79.4),
                cuisine = Cuisine("Halal"),
                cuttingMethod = CuttingMethod.HAND_CUT,
                ownerId = owner.id,
                rating = Rating(BigDecimal("4.8")),
            )

            val saved = listings.save(listing)
            saved.rating?.value shouldBe BigDecimal("4.8")

            // Rating round-trips through the source read path (NUMERIC(3,2) -> scale-2 BigDecimal).
            val found = listings.findById(saved.id)!!
            found.rating?.value shouldBe BigDecimal("4.80")

            // The search projection mirrors the rating (the search reads
            // listing_search rating for the minRating filter).
            val mirroredRating = jdbc.queryForObject(
                "SELECT rating FROM listing_search WHERE id = ?",
                BigDecimal::class.java,
                saved.id,
            )
            mirroredRating shouldBe BigDecimal("4.80")
        }

        test("save persists alcoholServed and mirrors it into the search projection (sc-118)") {
            val owner = accounts.save(Account.new(email = Email("alc-${UUID.randomUUID()}@example.com"), passwordHash = "argon2id\$h"))
            val listing = RestaurantListing.new(
                name = "Wine Pairing Grill",
                address = "8 Cork Ave",
                location = LatLng(43.7, -79.4),
                cuisine = Cuisine("Halal"),
                cuttingMethod = CuttingMethod.HAND_CUT,
                ownerId = owner.id,
                alcoholServed = true,
            )

            val saved = listings.save(listing)
            saved.alcoholServed shouldBe true

            // alcoholServed round-trips through the source read path.
            val found = listings.findById(saved.id)!!
            found.alcoholServed shouldBe true

            // The search projection mirrors the flag (sc-118 display attribute).
            val mirrored = jdbc.queryForObject(
                "SELECT alcohol_served FROM listing_search WHERE id = ?",
                Boolean::class.java,
                saved.id,
            )
            mirrored shouldBe true
        }

        test("save defaults alcoholServed to false (sc-118)") {
            val owner = accounts.save(Account.new(email = Email("alc-none-${UUID.randomUUID()}@example.com"), passwordHash = "argon2id\$h"))
            val listing = RestaurantListing.new(
                name = "Dry Grill",
                address = "9 Dry Blvd",
                location = LatLng(43.7, -79.4),
                cuisine = Cuisine("Halal"),
                cuttingMethod = CuttingMethod.HAND_CUT,
                ownerId = owner.id,
            )

            val saved = listings.save(listing)
            saved.alcoholServed shouldBe false

            val found = listings.findById(saved.id)!!
            found.alcoholServed shouldBe false
        }

        test("V11 adds an alcohol_served column defaulting to false (source + search projection)") {
            val sourceDefault = jdbc.queryForObject(
                "SELECT column_default FROM information_schema.columns " +
                    "WHERE table_name = 'restaurant_listings' AND column_name = 'alcohol_served'",
                String::class.java,
            )
            sourceDefault shouldBe "false"

            val sourceCol = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.columns WHERE table_name = 'restaurant_listings' AND column_name = 'alcohol_served'",
                Int::class.java,
            )
            sourceCol shouldBe 1

            val searchCol = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.columns WHERE table_name = 'listing_search' AND column_name = 'alcohol_served'",
                Int::class.java,
            )
            searchCol shouldBe 1
        }

        test("V10 adds a rating column to the source listing and the search projection") {
            val sourceRating = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.columns WHERE table_name = 'restaurant_listings' AND column_name = 'rating'",
                Int::class.java,
            )
            sourceRating shouldBe 1

            val searchRating = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.columns WHERE table_name = 'listing_search' AND column_name = 'rating'",
                Int::class.java,
            )
            searchRating shouldBe 1
        }

        test("V9 adds the multi-cuisine join table and a price column to the search projection") {
            val tables = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_name = 'restaurant_listing_cuisines'",
                Int::class.java,
            )
            tables shouldBe 1

            val priceCol = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.columns WHERE table_name = 'listing_search' AND column_name = 'price'",
                Int::class.java,
            )
            priceCol shouldBe 1
        }

        test("V9 enforces a positive price (CHECK) at the DB layer") {
            shouldThrow<DataIntegrityViolationException> {
                jdbc.update(
                    """
                    INSERT INTO restaurant_listings (name, address, location, cuisine, cutting_method, verification_status, price)
                    VALUES ('Neg Grill', '1 St', ST_SetSRID(ST_MakePoint(-74.0, 40.0), 4326)::geography, 'grill', 'UNSPECIFIED', 'UNVERIFIED', -1.0)
                    """.trimIndent(),
                )
            }
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

        test("migration enforces NOT NULL on every still-required column") {
            val owner = newOwner()
            shouldThrow<DataIntegrityViolationException> { tryInsert(owner = owner, name = null) }
            shouldThrow<DataIntegrityViolationException> { tryInsert(owner = owner, address = null) }
            shouldThrow<DataIntegrityViolationException> {
                tryInsert(owner = owner, locationExpr = "NULL")
            }
            shouldThrow<DataIntegrityViolationException> { tryInsert(owner = owner, cuttingMethod = null) }
        }

        test("migration accepts NULL cuisine, owner and provenance (community seed contract)") {
            // V6 makes cuisine/owner_id nullable for research-seed rows; brand_id
            // and provenance are nullable too. A row with all of them NULL is legal.
            tryInsert(owner = null, cuisine = null)
        }

        test("migration enforces the cuisine VARCHAR(64) boundary") {
            tryInsert(owner = newOwner(), cuisine = "c".repeat(64))
            shouldThrow<DataIntegrityViolationException> {
                tryInsert(owner = newOwner(), cuisine = "c".repeat(65))
            }
        }
    }
}
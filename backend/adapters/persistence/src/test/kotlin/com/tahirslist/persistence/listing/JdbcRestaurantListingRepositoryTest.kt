package com.tahirslist.persistence.listing

import com.tahirslist.domain.account.Account
import com.tahirslist.domain.account.Email
import com.tahirslist.domain.restaurant.CrossContamination
import com.tahirslist.domain.restaurant.Cuisine
import com.tahirslist.domain.restaurant.HalalItem
import com.tahirslist.domain.restaurant.HalalScope
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
        isHandCut: Boolean? = true,
        verificationStatus: String? = "UNVERIFIED",
    ): Int = jdbc.update(
        """
        INSERT INTO restaurant_listings (name, address, location, cuisine, is_hand_cut, owner_id, verification_status, cross_contamination)
        VALUES (?, ?, $locationExpr, ?, ?, ?, ?, 'NO_CROSS_CONTAMINATION')
        """.trimIndent(),
        name, address, cuisine, isHandCut, owner, verificationStatus,
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
                isHandCut = true,
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
            found.isHandCut shouldBe true
            found.verificationStatus shouldBe VerificationStatus.UNVERIFIED
            found.brandId shouldBe null
            found.provenance shouldBe null
            found.location.lat shouldBe (40.7128 plusOrMinus 0.0001)
            found.location.lng shouldBe (-74.0060 plusOrMinus 0.0001)
        }

        test("save round-trips the isHandCut boolean tri-state (sc-42)") {
            val owner = accounts.save(Account.new(email = Email("handcut-${UUID.randomUUID()}@example.com"), passwordHash = "argon2id\$h"))
            fun roundTrip(handCut: Boolean?): RestaurantListing? {
                val listing = RestaurantListing.new(
                    name = "Cut Grill", address = "1 Knife Ave", location = LatLng(43.7, -79.4),
                    cuisine = Cuisine("x"), isHandCut = handCut, ownerId = owner.id,
                )
                return listings.findById(listings.save(listing).id)
            }
            roundTrip(true)!!.isHandCut shouldBe true
            roundTrip(false)!!.isHandCut shouldBe false
            roundTrip(null)!!.isHandCut shouldBe null
        }

        test("save round-trips the isDelivery boolean tri-state (sc-184)") {
            val owner = accounts.save(Account.new(email = Email("deliv-${UUID.randomUUID()}@example.com"), passwordHash = "argon2id\$h"))
            fun roundTrip(delivery: Boolean?): RestaurantListing? {
                val listing = RestaurantListing.new(
                    name = "Deliver Grill", address = "1 Grub Ave", location = LatLng(43.7, -79.4),
                    cuisine = Cuisine("x"), isHandCut = true, isDelivery = delivery, ownerId = owner.id,
                )
                return listings.findById(listings.save(listing).id)
            }
            roundTrip(true)!!.isDelivery shouldBe true
            roundTrip(false)!!.isDelivery shouldBe false
            roundTrip(null)!!.isDelivery shouldBe null
        }

        test("V18 adds an is_delivery column to the source listing and the search projection (sc-184)") {
            for (table in listOf("restaurant_listings", "listing_search")) {
                val col = jdbc.queryForMap(
                    "SELECT data_type, is_nullable FROM information_schema.columns " +
                        "WHERE table_name = ? AND column_name = 'is_delivery'",
                    table,
                )
                col["data_type"] shouldBe "boolean"
                col["is_nullable"] shouldBe "YES" // null = unknown / not claimed (sc-42 null semantics)
            }
        }

        test("save persists isDelivery and mirrors it into the search projection (sc-184)") {
            val owner = accounts.save(Account.new(email = Email("deliv-mirror-${UUID.randomUUID()}@example.com"), passwordHash = "argon2id\$h"))
            val listing = RestaurantListing.new(
                name = "Delivery Grill",
                address = "4 Delivery Dr",
                location = LatLng(43.7, -79.4),
                cuisine = Cuisine("Halal"),
                isHandCut = true,
                isDelivery = true,
                // Index-qualified so save() mirrors it into the search projection
                // (sc-119 gate: only NO_CROSS_CONTAMINATION listings are indexed).
                crossContamination = CrossContamination.NO_CROSS_CONTAMINATION,
                ownerId = owner.id,
            )

            val saved = listings.save(listing)
            saved.isDelivery shouldBe true

            // isDelivery round-trips through the source read path.
            val found = listings.findById(saved.id)!!
            found.isDelivery shouldBe true

            // The search projection mirrors the flag (sc-184 reads listing_search only).
            val mirrored = jdbc.queryForObject(
                "SELECT is_delivery FROM listing_search WHERE id = ?",
                Boolean::class.java,
                saved.id,
            )
            mirrored shouldBe true
        }

        test("save defaults isDelivery to null (unknown) (sc-184)") {
            val owner = accounts.save(Account.new(email = Email("deliv-none-${UUID.randomUUID()}@example.com"), passwordHash = "argon2id\$h"))
            val listing = RestaurantListing.new(
                name = "Pickup Grill",
                address = "9 Dry Blvd",
                location = LatLng(43.7, -79.4),
                cuisine = Cuisine("Halal"),
                isHandCut = true,
                ownerId = owner.id,
            )

            val saved = listings.save(listing)
            saved.isDelivery shouldBe null

            val found = listings.findById(saved.id)!!
            found.isDelivery shouldBe null
        }

        test("findById returns null for an unknown id") {
            listings.findById(UUID.randomUUID()) shouldBe null
        }

        test("updateVerificationStatus promotes the listing AND the search mirror atomically (sc-73)") {
            val owner = accounts.save(Account.new(email = Email("vpromote@example.com"), passwordHash = "argon2id\$h"))
            val listing = RestaurantListing.new(
                name = "Promotable Grill",
                address = "1 Verify Ave",
                location = LatLng(43.7, -79.4),
                cuisine = Cuisine("mediterranean"),
                isHandCut = true,
                ownerId = owner.id,
                crossContamination = CrossContamination.NO_CROSS_CONTAMINATION,
            )
            val saved = listings.save(listing)
            saved.verificationStatus shouldBe VerificationStatus.UNVERIFIED

            val updated = listings.updateVerificationStatus(saved.id, VerificationStatus.VERIFIED)

            updated!!.verificationStatus shouldBe VerificationStatus.VERIFIED
            listings.findById(saved.id)!!.verificationStatus shouldBe VerificationStatus.VERIFIED
            // the public read mirror must reflect the promotion too (sc-73 → sc-49)
            val mirrorStatus = jdbc.queryForObject(
                "SELECT verification_status FROM listing_search WHERE id = ?",
                String::class.java,
                saved.id,
            )
            mirrorStatus shouldBe "VERIFIED"
        }

        test("updateVerificationStatus returns null for an unknown id and writes nothing") {
            listings.updateVerificationStatus(UUID.randomUUID(), VerificationStatus.VERIFIED) shouldBe null
        }

        test("save mirrors the listing into the listing_search read model atomically (sc-10)") {
            val owner = accounts.save(Account.new(email = Email("mirror@example.com"), passwordHash = "argon2id\$h"))
            val listing = RestaurantListing.new(
                name = "Mirror Test Grill",
                address = "9 Search Rd",
                location = LatLng(43.7, -79.4),
                cuisine = Cuisine("Halal"),
                isHandCut = true,
                ownerId = owner.id,
                crossContamination = CrossContamination.NO_CROSS_CONTAMINATION,
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
                isHandCut = true,
                ownerId = owner.id,
                price = Price(BigDecimal("15.50")),
                crossContamination = CrossContamination.NO_CROSS_CONTAMINATION,
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
                isHandCut = true,
                ownerId = owner.id,
                rating = Rating(BigDecimal("4.8")),
                crossContamination = CrossContamination.NO_CROSS_CONTAMINATION,
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
                isHandCut = true,
                ownerId = owner.id,
                alcoholServed = true,
                crossContamination = CrossContamination.NO_CROSS_CONTAMINATION,
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
                isHandCut = true,
                ownerId = owner.id,
            )

            val saved = listings.save(listing)
            saved.alcoholServed shouldBe false

            val found = listings.findById(saved.id)!!
            found.alcoholServed shouldBe false
        }

        test("save round-trips isHandCut, halalScope, halalItems and a qualified cross-contamination (sc-119)") {
            val owner = accounts.save(Account.new(email = Email("sc119-${UUID.randomUUID()}@example.com"), passwordHash = "argon2id\$h"))
            val listing = RestaurantListing.new(
                name = "Partial Halal Grill",
                address = "10 Scope Rd",
                location = LatLng(43.7, -79.4),
                cuisine = Cuisine("mediterranean"),
                ownerId = owner.id,
                isHandCut = true,
                halalScope = HalalScope.PARTIALLY_HALAL,
                halalItems = setOf(HalalItem("chicken", true), HalalItem("beef", false)),
                crossContamination = CrossContamination.NO_CROSS_CONTAMINATION,
            )

            val saved = listings.save(listing)
            saved.isHandCut shouldBe true

            val found = listings.findById(saved.id)!!
            found.isHandCut shouldBe true
            found.halalScope shouldBe HalalScope.PARTIALLY_HALAL
            found.halalItems shouldBe setOf(HalalItem("chicken", true), HalalItem("beef", false))
            found.crossContamination shouldBe CrossContamination.NO_CROSS_CONTAMINATION

            // Per-item halal scope persisted in the child table.
            val itemCount = jdbc.queryForObject(
                "SELECT count(*) FROM restaurant_halal_items WHERE listing_id = ?",
                Int::class.java,
                saved.id,
            )
            itemCount shouldBe 2
        }

        test("save defaults isHandCut/halalScope and crossContamination to the conservative unclaimed posture (sc-119)") {
            val owner = accounts.save(Account.new(email = Email("sc119-default-${UUID.randomUUID()}@example.com"), passwordHash = "argon2id\$h"))
            val listing = RestaurantListing.new(
                name = "Unclaimed Grill",
                address = "11 Default Rd",
                location = LatLng(43.7, -79.4),
                cuisine = Cuisine("mediterranean"),
                ownerId = owner.id,
            )

            val saved = listings.save(listing)
            saved.isHandCut shouldBe null
            saved.halalScope shouldBe HalalScope.NOT_DISCLOSED
            saved.crossContamination shouldBe CrossContamination.UNCERTAIN
        }

        test("the cross-contamination index gate excludes PRESENT/UNCERTAIN listings from the search mirror (sc-119)") {
            val owner = accounts.save(Account.new(email = Email("sc119-gate-${UUID.randomUUID()}@example.com"), passwordHash = "argon2id\$h"))
            fun saveQualified(cc: CrossContamination): UUID =
                listings.save(
                    RestaurantListing.new(
                        name = "Gate ${cc} ${UUID.randomUUID()}",
                        address = "12 Gate Rd",
                        location = LatLng(43.7, -79.4),
                        cuisine = Cuisine("mediterranean"),
                        ownerId = owner.id,
                        crossContamination = cc,
                    ),
                ).id

            val qualifiedId = saveQualified(CrossContamination.NO_CROSS_CONTAMINATION)
            val presentId = saveQualified(CrossContamination.PRESENT)
            val uncertainId = saveQualified(CrossContamination.UNCERTAIN)

            // Only the NO_CROSS_CONTAMINATION listing appears in the index.
            val inIndex = jdbc.query(
                "SELECT id FROM listing_search WHERE id IN (?, ?, ?)",
                { rs, _ -> rs.getObject("id", UUID::class.java) },
                qualifiedId, presentId, uncertainId,
            )
            inIndex shouldBe listOf(qualifiedId)
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
                    INSERT INTO restaurant_listings (name, address, location, cuisine, is_hand_cut, verification_status, price, cross_contamination)
                    VALUES ('Neg Grill', '1 St', ST_SetSRID(ST_MakePoint(-74.0, 40.0), 4326)::geography, 'grill', NULL, 'UNVERIFIED', -1.0, 'NO_CROSS_CONTAMINATION')
                    """.trimIndent(),
                )
            }
        }

        test("migration enforces the verification_status contract (UNVERIFIED and VERIFIED only)") {
            // Default insert is allowed (listing-first model: starts UNVERIFIED).
            tryInsert(owner = newOwner())
            // V15 (sc-73) admits VERIFIED -- the ONLY legal promotion, reached via
            // the Verification Committee approve path. Still-rejected:
            tryInsert(owner = newOwner(), verificationStatus = "VERIFIED")
            shouldThrow<DataIntegrityViolationException> {
                tryInsert(owner = newOwner(), verificationStatus = "NOT_A_STATUS")
            }
        }

        test("V17 replaces cutting_method with a nullable boolean is_hand_cut (sc-42)") {
            // The machine-cut vocabulary is gone from the write path. is_hand_cut
            // is a plain, nullable boolean: true / false / NULL (unknown).
            val methodCol = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.columns WHERE table_name = 'restaurant_listings' AND column_name = 'cutting_method'",
                Int::class.java,
            )
            methodCol shouldBe 0

            val handCutCol = jdbc.queryForMap(
                "SELECT data_type, is_nullable FROM information_schema.columns " +
                    "WHERE table_name = 'restaurant_listings' AND column_name = 'is_hand_cut'",
            )
            handCutCol["data_type"] shouldBe "boolean"
            handCutCol["is_nullable"] shouldBe "YES"

            // NULL (unknown / not claimed) is legal — no CHECK vocabulary to violate.
            tryInsert(owner = newOwner(), isHandCut = null)
            tryInsert(owner = newOwner(), isHandCut = false)
        }

        test("V18 enforces the cross_contamination CHECK and adds halal_scope (sc-119)") {
            // cross_contamination and halal_scope are closed vocabularies enforced
            // at the DB layer; an out-of-vocabulary value is rejected.
            val ccCols = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.columns WHERE table_name = 'restaurant_listings' AND column_name = 'cross_contamination'",
                Int::class.java,
            )
            ccCols shouldBe 1

            val scopeCols = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.columns WHERE table_name = 'restaurant_listings' AND column_name = 'halal_scope'",
                Int::class.java,
            )
            scopeCols shouldBe 1

            shouldThrow<DataIntegrityViolationException> {
                jdbc.update(
                    """
                    INSERT INTO restaurant_listings (name, address, location, cuisine, is_hand_cut, owner_id, verification_status, cross_contamination)
                    VALUES ('X', '1 St', ST_SetSRID(ST_MakePoint(-74.0, 40.0), 4326)::geography, 'grill', NULL, ?, 'UNVERIFIED', 'BOGUS')
                    """.trimIndent(),
                    newOwner(),
                )
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
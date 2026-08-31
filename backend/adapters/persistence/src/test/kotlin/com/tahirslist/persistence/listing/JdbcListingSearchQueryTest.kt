package com.tahirslist.persistence.listing

import com.tahirslist.domain.restaurant.LatLng
import com.tahirslist.application.listing.CuttingMethodFilter
import com.tahirslist.application.listing.CuisineLogic
import com.tahirslist.application.listing.ListingSearchFilters
import com.tahirslist.application.listing.ListingSearchQuery
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeBlank
import org.flywaydb.core.Flyway
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.math.BigDecimal
import java.util.UUID

/**
 * sc-10 location search (docs: Shortcut sc-10; contract ratified in task
 * t_847010c3): proves V8 creates the denormalised `listing_search` projection
 * and that [JdbcListingSearchQuery] returns listings within a radius, ordered by
 * straight-line distance ascending, with distance in miles, using offset paging —
 * against the real seeded (V7) PostGIS data.
 */
class JdbcListingSearchQueryTest : FunSpec() {

    private val postgres: PostgreSQLContainer<*> = PostgreSQLContainer(
        DockerImageName.parse("postgis/postgis:17-3.4").asCompatibleSubstituteFor("postgres"),
    )
        .withDatabaseName("test")
        .withUsername("test")
        .withPassword("test")

    private lateinit var jdbc: JdbcTemplate
    private lateinit var query: ListingSearchQuery

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
            query = JdbcListingSearchQuery(jdbc)
        }
        afterSpec { postgres.stop() }

        test("V8 creates the listing_search table with a GiST index on location") {
            val count = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_name = 'listing_search'",
                Int::class.java,
            )
            count shouldBe 1

            val index = jdbc.queryForObject(
                """
                SELECT count(*) FROM pg_indexes
                WHERE tablename = 'listing_search' AND indexdef ILIKE '%USING gist (location)%'
                """.trimIndent(),
                Int::class.java,
            )
            index shouldBe 1
        }

        test("V8 backfilled the seeded listings into listing_search") {
            // All 30 V7 seed rows are immediately searchable (denormalised from
            // restaurant_listings, not queryable only if separately ingested).
            val backfilled = jdbc.queryForObject(
                "SELECT count(*) FROM listing_search",
                Int::class.java,
            )
            backfilled shouldBe 30
        }

        test("returns listings within the radius, ordered by distance ascending, with distance in miles") {
            // Center on Osmow's (St. Clair, Toronto) — the nearest seed row.
            val center = LatLng(lat = 43.682921, lng = -79.418493)

            val results = query.searchNearby(center = center, radiusMiles = 5.0, offset = 0, limit = 50)

            // Osmow's itself is ~0 miles away and must be the nearest result.
            results.firstOrNull()?.name shouldBe "Osmow's"
            results.firstOrNull()!!.distanceMiles.shouldBeLessThan(0.5)

            // Ordered ascending by straight-line distance.
            val distances = results.map { it.distanceMiles }
            distances.zipWithNext().forEach { (a, b) -> (a <= b) shouldBe true }

            // All within the requested radius.
            results.forEach { (it.distanceMiles <= 5.0) shouldBe true }

            // A couple of other Toronto seeds within 5 miles of St. Clair.
            results.map { it.name } shouldContain "The Halal Guys"
            results.map { it.name } shouldContain "Lazeez Shawarma"
            results.map { it.name } shouldContain "Aroma Fine Indian Cuisine"
        }

        test("excludes listings beyond the radius") {
            val center = LatLng(lat = 43.682921, lng = -79.418493)

            // Tiny radius around Osmow's: only the co-located listing qualifies.
            val results = query.searchNearby(center = center, radiusMiles = 0.1, offset = 0, limit = 50)

            results.size shouldBe 1
            results.single().name shouldBe "Osmow's"
        }

        test("returns an empty list for a center with nothing nearby (no crash)") {
            // Somewhere in the Indian Ocean — far from every seed.
            val middleOfNowhere = LatLng(lat = -30.0, lng = 70.0)

            val results = query.searchNearby(center = middleOfNowhere, radiusMiles = 500.0, offset = 0, limit = 50)

            results shouldNotBe null
            results shouldBe emptyList()
        }

        test("offset paging returns the next page after the first") {
            val center = LatLng(lat = 43.682921, lng = -79.418493)

            val pageOne = query.searchNearby(center = center, radiusMiles = 5.0, offset = 0, limit = 2)
            val pageTwo = query.searchNearby(center = center, radiusMiles = 5.0, offset = 2, limit = 50)

            pageOne.size shouldBe 2
            (pageTwo.size >= 1) shouldBe true

            // Same ordering, disjoint pages.
            val allIds = (pageOne + pageTwo).map { it.id }
            allIds.distinct().size shouldBe allIds.size
            // Page two's first result must be strictly farther than page one's last.
            (pageTwo.first().distanceMiles >= pageOne.last().distanceMiles) shouldBe true
        }

        test("counts by name can be asserted via the return type carrying the full card fields") {
            val center = LatLng(lat = 43.682921, lng = -79.418493)

            val results = query.searchNearby(center = center, radiusMiles = 5.0, offset = 0, limit = 50)

            // Each result carries the address + verification status so the read
            // surface need not re-join to build a card.
            val osmow = results.first { it.name == "Osmow's" }
            osmow.address.shouldNotBeBlank()
            osmow.verificationStatus.name shouldBe "UNVERIFIED"
            osmow.cuttingMethod.name shouldBe "UNSPECIFIED"
        }

        test("cuttingMethod filter narrows results to the matching stored method") {
            // sc-42: insert controlled HAND_CUT / MACHINE_CUT rows at a location
            // far from every seed, so these assertions are isolated from the 30
            // UNSPECIFIED seed rows and from the other tests in this spec. The
            // mirror into listing_search reproduces exactly what save() does.
            insertCuttingTestRows()
            val center = LatLng(lat = 45.0, lng = -79.0)

            // BOTH ("any") matches every method — both inserted rows.
            val both = query.searchNearby(center = center, radiusMiles = 5.0, filters = ListingSearchFilters(cuttingMethod = CuttingMethodFilter.BOTH), offset = 0, limit = 50)
            both.map { it.name }.toSet() shouldBe setOf("Hand Cut Test", "Machine Cut Test")

            // HAND_CUT matches only the hand-cut row.
            val handCut = query.searchNearby(center = center, radiusMiles = 5.0, filters = ListingSearchFilters(cuttingMethod = CuttingMethodFilter.HAND_CUT), offset = 0, limit = 50)
            handCut.map { it.name } shouldBe listOf("Hand Cut Test")
            handCut.single().cuttingMethod.name shouldBe "HAND_CUT"

            // MACHINE_CUT matches only the machine-cut row.
            val machineCut = query.searchNearby(center = center, radiusMiles = 5.0, filters = ListingSearchFilters(cuttingMethod = CuttingMethodFilter.MACHINE_CUT), offset = 0, limit = 50)
            machineCut.map { it.name } shouldBe listOf("Machine Cut Test")
            machineCut.single().cuttingMethod.name shouldBe "MACHINE_CUT"
        }

        test("cuttingMethod filter combines with the location radius") {
            // sc-42: the filter must not bypass the radius. Nesting the filter
            // around Osmow's (UNSPECIFIED) co-located seed with a tiny radius:
            // HAND_CUT finds nothing there, while BOTH still finds Osmow's.
            val center = LatLng(lat = 43.682921, lng = -79.418493)

            val handCut = query.searchNearby(center = center, radiusMiles = 0.1, filters = ListingSearchFilters(cuttingMethod = CuttingMethodFilter.HAND_CUT), offset = 0, limit = 50)
            handCut shouldBe emptyList()

            val both = query.searchNearby(center = center, radiusMiles = 0.1, filters = ListingSearchFilters(cuttingMethod = CuttingMethodFilter.BOTH), offset = 0, limit = 50)
            both.single().name shouldBe "Osmow's"
        }

        test("price range filter narrows results to listings whose price falls inside the range") {
            // sc-43: insert controlled priced rows far from the seeds so these
            // assertions are isolated from the 30 NULL-price seed rows.
            insertFilterTestRows()
            val center = LatLng(lat = 46.0, lng = -78.0)

            // No price filter -> every controlled row within the radius.
            val all = query.searchNearby(center = center, radiusMiles = 5.0, offset = 0, limit = 50)
            all.map { it.name }.toSet() shouldBe setOf("Taco Mixto", "Taco Solo", "Med Grill", "Budget Eats", "No Price Wagyu")

            // min + max bound the range (inclusive).
            val mid = query.searchNearby(
                center = center, radiusMiles = 5.0,
                filters = ListingSearchFilters(minPrice = BigDecimal("12"), maxPrice = BigDecimal("18")),
                offset = 0, limit = 50,
            )
            mid.map { it.name } shouldBe listOf("Taco Mixto")

            // minPrice only.
            val minOnly = query.searchNearby(
                center = center, radiusMiles = 5.0,
                filters = ListingSearchFilters(minPrice = BigDecimal("11")),
                offset = 0, limit = 50,
            )
            minOnly.map { it.name }.toSet() shouldBe setOf("Taco Mixto", "Med Grill")

            // A NULL-price row never matches a price filter (V6 null semantics).
            val low = query.searchNearby(
                center = center, radiusMiles = 5.0,
                filters = ListingSearchFilters(maxPrice = BigDecimal("7")),
                offset = 0, limit = 50,
            )
            low.map { it.name } shouldBe listOf("Budget Eats")
        }

        test("cuisine filter with OR (default) matches a listing with ANY selected cuisine") {
            // sc-44: OR is the PRD default. A multi-cuisine listing matches via any
            // one of its cuisines; a NULL-cuisine listing never matches.
            insertFilterTestRows()
            val center = LatLng(lat = 46.0, lng = -78.0)

            // Mixed case proves the filter is normalised before matching the
            // lowercase-stored cuisine values.
            val or = query.searchNearby(
                center = center, radiusMiles = 5.0,
                filters = ListingSearchFilters(cuisines = listOf("Mexican", "Mediterranean")),
                offset = 0, limit = 50,
            )
            or.map { it.name }.toSet() shouldBe setOf("Taco Mixto", "Taco Solo", "Med Grill")
        }

        test("cuisine filter with AND matches only a listing that has ALL selected cuisines") {
            // sc-44: AND is the multi-cuisine case — only the listing carrying both
            // selected cuisines qualifies. The card still reports the primary cuisine.
            insertFilterTestRows()
            val center = LatLng(lat = 46.0, lng = -78.0)

            val and = query.searchNearby(
                center = center, radiusMiles = 5.0,
                filters = ListingSearchFilters(cuisines = listOf("mexican", "mediterranean"), cuisineLogic = CuisineLogic.AND),
                offset = 0, limit = 50,
            )
            and.map { it.name } shouldBe listOf("Taco Mixto")
            and.single().cuisine?.value shouldBe "mexican"
        }

        test("cuisine filter never matches a listing with no cuisines (NULL-cuisine seed contract)") {
            insertFilterTestRows()
            val center = LatLng(lat = 46.0, lng = -78.0)

            val mex = query.searchNearby(
                center = center, radiusMiles = 5.0,
                filters = ListingSearchFilters(cuisines = listOf("mexican")),
                offset = 0, limit = 50,
            )
            mex.map { it.name }.toSet() shouldBe setOf("Taco Mixto", "Taco Solo")
        }

        test("price + cuisine + cuttingMethod filters combine with the location radius") {
            // sc-43 + sc-44 + sc-42: a listing must satisfy every active predicate
            // simultaneously (hand-cut AND mexican AND price 9..16, within radius).
            insertFilterTestRows()
            val center = LatLng(lat = 46.0, lng = -78.0)

            val combined = query.searchNearby(
                center = center, radiusMiles = 5.0,
                filters = ListingSearchFilters(
                    cuttingMethod = CuttingMethodFilter.HAND_CUT,
                    cuisines = listOf("mexican"),
                    minPrice = BigDecimal("9"),
                    maxPrice = BigDecimal("16"),
                ),
                offset = 0, limit = 50,
            )
            combined.map { it.name } shouldBe listOf("Taco Mixto")
        }
    }

    /**
     * Inserts five controlled filter-test rows at 46N/78W (far from the seeds)
     * and mirrors them into listing_search exactly as save() does, so the
     * price/cuisine assertions are isolated from the seed rows and the cutting
     * rows at 45N/79W. Idempotent via ON CONFLICT.
     */
    private fun insertFilterTestRows() {
        // (id, name, cuisine, cutting, price, cuisines)
        val rows = listOf(
            listOf("20000000-0000-0000-0000-000000000001", "Taco Mixto", "mexican", "HAND_CUT", "15.00", listOf("mexican", "mediterranean")),
            listOf("20000000-0000-0000-0000-000000000002", "Taco Solo", "mexican", "UNSPECIFIED", "10.00", listOf("mexican")),
            listOf("20000000-0000-0000-0000-000000000003", "Med Grill", "mediterranean", "HAND_CUT", "20.00", listOf("mediterranean")),
            listOf("20000000-0000-0000-0000-000000000004", "Budget Eats", null, "UNSPECIFIED", "5.00", emptyList<String>()),
            listOf("20000000-0000-0000-0000-000000000005", "No Price Wagyu", null, "UNSPECIFIED", null, emptyList<String>()),
        )
        val ids = rows.map { UUID.fromString(it[0] as String) }

        rows.forEach { row ->
            jdbc.update(
                """
                INSERT INTO restaurant_listings (id, name, address, location, cuisine, cutting_method, verification_status, price)
                VALUES (?, ?, ?, ST_SetSRID(ST_MakePoint(-78.0, 46.0), 4326)::geography, ?, ?, 'UNVERIFIED', ?)
                ON CONFLICT (id) DO NOTHING
                """.trimIndent(),
                UUID.fromString(row[0] as String),
                row[1] as String,
                "46.00, -78.00",
                row[2] as String?,
                row[3] as String,
                (row[4] as String?)?.let { java.math.BigDecimal(it) },
            )
        }

        // Mirror into the denormalised projection, exactly the sc-10 save() contract.
        jdbc.update(
            """
            INSERT INTO listing_search (id, name, address, location, cuisine, cutting_method, verification_status, price)
            SELECT id, name, address, location, cuisine, cutting_method, verification_status, price
            FROM restaurant_listings WHERE id IN (?, ?, ?, ?, ?)
            ON CONFLICT (id) DO NOTHING
            """.trimIndent(),
            ids[0], ids[1], ids[2], ids[3], ids[4],
        )

        // Multi-cuisine rows.
        rows.filter { (it[5] as List<*>).isNotEmpty() }.forEach { row ->
            val id = UUID.fromString(row[0] as String)
            (row[5] as List<*>).forEach { cuisine ->
                jdbc.update(
                    """
                    INSERT INTO restaurant_listing_cuisines (listing_id, cuisine)
                    VALUES (?, ?)
                    ON CONFLICT (listing_id, cuisine) DO NOTHING
                    """.trimIndent(),
                    id, cuisine as String,
                )
            }
        }
    }

    /** Inserts two controlled cutting-test rows far from the seeds and mirrors them into listing_search. Idempotent. */
    private fun insertCuttingTestRows() {
        val handId = UUID.fromString("10000000-0000-0000-0000-000000000001")
        val machineId = UUID.fromString("10000000-0000-0000-0000-000000000002")

        jdbc.update(
            """
            INSERT INTO restaurant_listings (id, name, address, location, cuisine, cutting_method, verification_status)
            VALUES (?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?, ?, 'UNVERIFIED')
            ON CONFLICT (id) DO NOTHING
            """.trimIndent(),
            handId, "Hand Cut Test", "45.00, -79.00", -79.0, 45.0, "Grill", "HAND_CUT",
        )
        jdbc.update(
            """
            INSERT INTO restaurant_listings (id, name, address, location, cuisine, cutting_method, verification_status)
            VALUES (?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?, ?, 'UNVERIFIED')
            ON CONFLICT (id) DO NOTHING
            """.trimIndent(),
            machineId, "Machine Cut Test", "45.00, -79.00", -79.0, 45.0, "Grill", "MACHINE_CUT",
        )
        // Mirror into the denormalised projection, exactly the sc-10 save() contract.
        jdbc.update(
            """
            INSERT INTO listing_search (id, name, address, location, cuisine, cutting_method, verification_status)
            SELECT id, name, address, location, cuisine, cutting_method, verification_status
            FROM restaurant_listings WHERE id IN (?, ?)
            ON CONFLICT (id) DO NOTHING
            """.trimIndent(),
            handId, machineId,
        )
    }
}
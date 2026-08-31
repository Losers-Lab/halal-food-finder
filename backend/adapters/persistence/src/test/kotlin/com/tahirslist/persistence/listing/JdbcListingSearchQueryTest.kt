package com.tahirslist.persistence.listing

import com.tahirslist.domain.restaurant.LatLng
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
    }
}
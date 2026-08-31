package com.tahirslist.bootstrap.listing

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.tahirslist.bootstrap.PostgresBootTest
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal
import java.util.UUID

/**
 * sc-10 location search endpoint (task t_847010c3): GET /v1/listings/search.
 *
 * Contract exercised end-to-end against the real Flyway seed data:
 *  1. `?center=<lat,lon>&radius=<miles>` returns listings within radius, ordered
 *     distance ascending, each carrying `distanceMiles` in straight-line miles;
 *  2. a centre with nothing nearby returns an empty list (no crash);
 *  3. malformed / missing centre or radius returns 400 `invalid_input` (not 500);
 *  4. the query is public (no token) like the rest of the sc-157 read surface.
 *
 * Works off `origin/wt/sc-171-live-seed-ingest`, which carries V6/V7 so the
 * 30-listing seed backs the assertions.
 */
class ListingSearchEndpointTest : PostgresBootTest() {

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var jdbc: JdbcTemplate

    private fun get(path: String): ResponseEntity<String> =
        restTemplate.getForEntity(path, String::class.java)

    private fun bodyOf(resp: ResponseEntity<String>): JsonNode = objectMapper.readTree(resp.body)

    init {
        test("search returns listings within the radius, ordered by distance ascending, with distanceMiles") {
            // Center on Osmow's (St. Clair, Toronto).
            val resp = get("/v1/listings/search?center=43.682921,-79.418493&radius=5.0")

            resp.statusCode shouldBe HttpStatus.OK
            val results: JsonNode = bodyOf(resp)
            results.isArray shouldBe true

            // Osmow's co-located with the centre — the nearest result.
            results[0].get("name").asText() shouldBe "Osmow's"
            (results[0].get("distanceMiles").asDouble() < 0.5) shouldBe true

            // Ordered ascending by distance.
            val distances = (0 until results.size()).map { results[it].get("distanceMiles").asDouble() }
            distances.zipWithNext().forEach { (a, b) -> (a <= b) shouldBe true }

            // Every result carries a distance within the radius and lands inside it.
            results.forEach { node ->
                (node.get("distanceMiles").asDouble() <= 5.0) shouldBe true
                node.has("id") shouldBe true
                node.has("name") shouldBe true
                node.has("address") shouldBe true
                node.has("lat") shouldBe true
                node.has("lng") shouldBe true
            }
        }

        test("search returns an empty array for a centre far from every listing (no crash)") {
            // Middle of the Indian Ocean.
            val resp = get("/v1/listings/search?center=-30.0,70.0&radius=500.0")

            resp.statusCode shouldBe HttpStatus.OK
            val results: JsonNode = bodyOf(resp)
            results.isArray shouldBe true
            results.size() shouldBe 0
        }

        test("search is public (no token required), like the rest of the read surface") {
            val resp = get("/v1/listings/search?center=43.682921,-79.418493&radius=5.0")

            resp.statusCode shouldBe HttpStatus.OK
        }

        test("a malformed centre returns 400 invalid_input, not a crash") {
            val resp = get("/v1/listings/search?center=not-a-latlong&radius=5.0")

            resp.statusCode shouldBe HttpStatus.BAD_REQUEST
            bodyOf(resp).get("code").asText() shouldBe "invalid_input"
        }

        test("a centre out of range returns 400 invalid_input") {
            val resp = get("/v1/listings/search?center=999.0,-999.0&radius=5.0")

            resp.statusCode shouldBe HttpStatus.BAD_REQUEST
            bodyOf(resp).get("code").asText() shouldBe "invalid_input"
        }

        test("a missing centre returns 400 invalid_input") {
            val resp = get("/v1/listings/search?radius=5.0")

            resp.statusCode shouldBe HttpStatus.BAD_REQUEST
        }

        test("a zero radius returns 400 invalid_input, not a crash") {
            val resp = get("/v1/listings/search?center=43.682921,-79.418493&radius=0.0")

            resp.statusCode shouldBe HttpStatus.BAD_REQUEST
        }

        test("a negative radius returns 400 invalid_input") {
            val resp = get("/v1/listings/search?center=43.682921,-79.418493&radius=-5.0")

            resp.statusCode shouldBe HttpStatus.BAD_REQUEST
        }

        test("cuttingMethod=HAND_CUT narrows the search and excludes the UNSPECIFIED seed") {
            // sc-42: the entire seed set is UNSPECIFIED, so a HAND_CUT filter
            // around St. Clair must return nothing, while the same query without
            // the filter returns hits — proving the filter actually narrows.
            val unfiltered = get("/v1/listings/search?center=43.682921,-79.418493&radius=5.0")
            (bodyOf(unfiltered).size() > 0) shouldBe true

            val handCut = get("/v1/listings/search?center=43.682921,-79.418493&radius=5.0&cuttingMethod=HAND_CUT")
            handCut.statusCode shouldBe HttpStatus.OK
            bodyOf(handCut).size() shouldBe 0
        }

        test("cuttingMethod=MACHINE_CUT narrows the search and excludes the UNSPECIFIED seed") {
            val machineCut = get("/v1/listings/search?center=43.682921,-79.418493&radius=5.0&cuttingMethod=MACHINE_CUT")

            machineCut.statusCode shouldBe HttpStatus.OK
            bodyOf(machineCut).size() shouldBe 0
        }

        test("cuttingMethod=BOTH matches the no-filter result set (any method)") {
            val both = get("/v1/listings/search?center=43.682921,-79.418493&radius=5.0&cuttingMethod=BOTH")

            both.statusCode shouldBe HttpStatus.OK
            val results = bodyOf(both)
            (results.size() > 0) shouldBe true
            results[0].get("name").asText() shouldBe "Osmow's"
        }

        test("an invalid cuttingMethod returns 400 invalid_input, not a crash") {
            val resp = get("/v1/listings/search?center=43.682921,-79.418493&radius=5.0&cuttingMethod=STUNK")

            resp.statusCode shouldBe HttpStatus.BAD_REQUEST
            bodyOf(resp).get("code").asText() shouldBe "invalid_input"
        }

        test("price range narrows results through the live search endpoint (sc-43)") {
            // Controlled priced rows far from the seeds (mirrored into listing_search).
            insertFilterRows()
            val base = "/v1/listings/search?center=45.5,-78.5&radius=2.0"

            val resp = get("$base&minPrice=12&maxPrice=18")
            resp.statusCode shouldBe HttpStatus.OK
            val names = bodyOf(resp).map { it.get("name").asText() }
            names shouldBe listOf("Taco Mixto")
        }

        test("cuisine filter OR (default, no cuisineLogic) matches any selected cuisine (sc-44)") {
            insertFilterRows()
            val base = "/v1/listings/search?center=45.5,-78.5&radius=2.0"

            // No cuisineLogic -> PRD-default OR. Multi-cuisine Taco Mixto matches
            // via either cuisine; NULL-cuisine Budget Eats / No Price Wagyu never match.
            val resp = get("$base&cuisine=mexican&cuisine=mediterranean")
            resp.statusCode shouldBe HttpStatus.OK
            bodyOf(resp).map { it.get("name").asText() }.toSet() shouldBe setOf("Taco Mixto", "Taco Solo", "Med Grill")
        }

        test("cuisine filter AND requires the listing to carry every selected cuisine (sc-44)") {
            insertFilterRows()
            val base = "/v1/listings/search?center=45.5,-78.5&radius=2.0"

            val resp = get("$base&cuisine=mexican&cuisine=mediterranean&cuisineLogic=AND")
            resp.statusCode shouldBe HttpStatus.OK
            val names = bodyOf(resp).map { it.get("name").asText() }
            names shouldBe listOf("Taco Mixto")
        }

        test("cuisine values are normalised (mixed case matches the lowercase-stored value)") {
            insertFilterRows()
            val base = "/v1/listings/search?center=45.5,-78.5&radius=2.0"

            val resp = get("$base&cuisine=MEXICAN")
            resp.statusCode shouldBe HttpStatus.OK
            bodyOf(resp).map { it.get("name").asText() }.toSet() shouldBe setOf("Taco Mixto", "Taco Solo")
        }

        test("an invalid cuisineLogic returns 400 invalid_input, not a crash") {
            val resp = get("/v1/listings/search?center=45.5,-78.5&radius=2.0&cuisine=mexican&cuisineLogic=BOTH")

            resp.statusCode shouldBe HttpStatus.BAD_REQUEST
            bodyOf(resp).get("code").asText() shouldBe "invalid_input"
        }

        test("a negative minPrice returns 400 invalid_input") {
            val resp = get("/v1/listings/search?center=45.5,-78.5&radius=2.0&minPrice=-5.0")

            resp.statusCode shouldBe HttpStatus.BAD_REQUEST
            bodyOf(resp).get("code").asText() shouldBe "invalid_input"
        }

        test("minPrice greater than maxPrice returns 400 invalid_input") {
            val resp = get("/v1/listings/search?center=45.5,-78.5&radius=2.0&minPrice=20.0&maxPrice=10.0")

            resp.statusCode shouldBe HttpStatus.BAD_REQUEST
            bodyOf(resp).get("code").asText() shouldBe "invalid_input"
        }

        test("minRating narrows results through the live search endpoint (sc-45)") {
            // Controlled rated rows far from the NULL-rating seeds.
            insertRatedEndpointRows()
            val base = "/v1/listings/search?center=47.0,-77.0&radius=2.0"

            val resp = get("$base&minRating=4.0")
            resp.statusCode shouldBe HttpStatus.OK
            val names = bodyOf(resp).map { it.get("name").asText() }
            names shouldBe listOf("Star Grill")
        }

        test("minRating combines with distance radius and all prior filters through the endpoint") {
            insertRatedEndpointRows()
            val base = "/v1/listings/search?center=47.0,-77.0&radius=2.0"

            val resp = get("$base&minRating=1.0&cuttingMethod=HAND_CUT&cuisine=mexican&minPrice=12&maxPrice=16")
            resp.statusCode shouldBe HttpStatus.OK
            val names = bodyOf(resp).map { it.get("name").asText() }
            names shouldBe listOf("Star Grill")
        }

        test("a negative minRating returns 400 invalid_input") {
            val resp = get("/v1/listings/search?center=45.5,-78.5&radius=2.0&minRating=-1.0")

            resp.statusCode shouldBe HttpStatus.BAD_REQUEST
            bodyOf(resp).get("code").asText() shouldBe "invalid_input"
        }

        test("a minRating above the 0..5 scale returns 400 invalid_input") {
            val resp = get("/v1/listings/search?center=45.5,-78.5&radius=2.0&minRating=5.5")

            resp.statusCode shouldBe HttpStatus.BAD_REQUEST
            bodyOf(resp).get("code").asText() shouldBe "invalid_input"
        }
    }

    /**
     * Inserts five controlled price/cuisine rows at 45.5N/78.5W and mirrors them
     * into listing_search, so the price/cuisine endpoint assertions are isolated
     * from the NULL-cuisine/NULL-price seed rows. Idempotent via ON CONFLICT —
     * the shared PostgresBootTest container accumulates rows across this spec, so
     * repeated runs must not duplicate.
     */
    private fun insertFilterRows() {
        val base = listOf(
            listOf("30000000-0000-0000-0000-000000000001", "Taco Mixto", "mexican", "HAND_CUT", "15.00", listOf("mexican", "mediterranean")),
            listOf("30000000-0000-0000-0000-000000000002", "Taco Solo", "mexican", "UNSPECIFIED", "10.00", listOf("mexican")),
            listOf("30000000-0000-0000-0000-000000000003", "Med Grill", "mediterranean", "UNSPECIFIED", "20.00", listOf("mediterranean")),
            listOf("30000000-0000-0000-0000-000000000004", "Budget Eats", null, "UNSPECIFIED", "5.00", emptyList<String>()),
            listOf("30000000-0000-0000-0000-000000000005", "No Price Wagyu", null, "UNSPECIFIED", null, emptyList<String>()),
        )
        base.forEach { row ->
            jdbc.update(
                """
                INSERT INTO restaurant_listings (id, name, address, location, cuisine, cutting_method, verification_status, price)
                VALUES (?, ?, ?, ST_SetSRID(ST_MakePoint(-78.5, 45.5), 4326)::geography, ?, ?, 'UNVERIFIED', ?)
                ON CONFLICT (id) DO NOTHING
                """.trimIndent(),
                UUID.fromString(row[0] as String), row[1] as String, "45.50, -78.50",
                row[2] as String?, row[3] as String, (row[4] as String?)?.let { BigDecimal(it) },
            )
        }
        jdbc.update(
            """
            INSERT INTO listing_search (id, name, address, location, cuisine, cutting_method, verification_status, price)
            SELECT id, name, address, location, cuisine, cutting_method, verification_status, price
            FROM restaurant_listings WHERE id IN (?, ?, ?, ?, ?)
            ON CONFLICT (id) DO NOTHING
            """.trimIndent(),
            UUID.fromString("30000000-0000-0000-0000-000000000001"),
            UUID.fromString("30000000-0000-0000-0000-000000000002"),
            UUID.fromString("30000000-0000-0000-0000-000000000003"),
            UUID.fromString("30000000-0000-0000-0000-000000000004"),
            UUID.fromString("30000000-0000-0000-0000-000000000005"),
        )
        base.filter { (it[5] as List<*>).isNotEmpty() }.forEach { row ->
            val id = UUID.fromString(row[0] as String)
            (row[5] as List<*>).forEach { cuisine ->
                jdbc.update(
                    "INSERT INTO restaurant_listing_cuisines (listing_id, cuisine) VALUES (?, ?) ON CONFLICT DO NOTHING",
                    id, cuisine as String,
                )
            }
        }
    }

    /**
     * Inserts four controlled rating rows at 47N/77W and mirrors them into
     * listing_search, isolating the rating endpoint assertions from the NULL-rating
     * seeds and the price/cuisine rows at 45.5N/78.5W. Idempotent via ON CONFLICT.
     */
    private fun insertRatedEndpointRows() {
        // (id, name, cuisine, cutting, price, rating)
        val rows = listOf(
            listOf("50000000-0000-0000-0000-000000000001", "Star Grill", "mexican", "HAND_CUT", "15.00", "4.8"),
            listOf("50000000-0000-0000-0000-000000000002", "Clover Cafe", "mexican", "UNSPECIFIED", "10.00", "3.5"),
            listOf("50000000-0000-0000-0000-000000000003", "Rustic Table", "mediterranean", "MACHINE_CUT", "20.00", "2.5"),
            listOf("50000000-0000-0000-0000-000000000004", "No Rating Bistro", null, "UNSPECIFIED", "5.00", null),
        )
        val ids = rows.map { UUID.fromString(it[0] as String) }

        rows.forEach { row ->
            jdbc.update(
                """
                INSERT INTO restaurant_listings (id, name, address, location, cuisine, cutting_method, verification_status, price, rating)
                VALUES (?, ?, ?, ST_SetSRID(ST_MakePoint(-77.0, 47.0), 4326)::geography, ?, ?, 'UNVERIFIED', ?, ?)
                ON CONFLICT (id) DO NOTHING
                """.trimIndent(),
                UUID.fromString(row[0] as String), row[1] as String, "47.00, -77.00",
                row[2] as String?, row[3] as String,
                (row[4] as String?)?.let { BigDecimal(it) }, (row[5] as String?)?.let { BigDecimal(it) },
            )
        }
        jdbc.update(
            """
            INSERT INTO listing_search (id, name, address, location, cuisine, cutting_method, verification_status, price, rating)
            SELECT id, name, address, location, cuisine, cutting_method, verification_status, price, rating
            FROM restaurant_listings WHERE id IN (?, ?, ?, ?)
            ON CONFLICT (id) DO NOTHING
            """.trimIndent(),
            ids[0], ids[1], ids[2], ids[3],
        )
        rows.filter { (it[2] as String?) != null }.forEach { row ->
            jdbc.update(
                "INSERT INTO restaurant_listing_cuisines (listing_id, cuisine) VALUES (?, ?) ON CONFLICT DO NOTHING",
                UUID.fromString(row[0] as String), row[2] as String,
            )
        }
    }
}
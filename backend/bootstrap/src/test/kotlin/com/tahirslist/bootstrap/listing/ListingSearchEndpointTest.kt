package com.tahirslist.bootstrap.listing

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.tahirslist.bootstrap.PostgresBootTest
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

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
    }
}
package com.tahirslist.bootstrap

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.client.ClientHttpResponse
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ContextConfiguration
import org.springframework.web.client.ResponseErrorHandler
import org.springframework.web.client.RestTemplate
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.UUID
import javax.sql.DataSource

/**
 * sc-138 HTTP surface for Add Listing: POST /v1/listings.
 *
 * End-to-end against the full application graph (controller -> CreateListing use
 * case -> JdbcRestaurantListingRepository) with the real Flyway V4 migration.
 * Covers the mandated cases: happy path, unauthenticated 401, validation 4xx,
 * owner-not-found, and a persistence round-trip (reads the row back from PostGIS).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = [ListingEndpointTest.JwtKeyInitializer::class])
class ListingEndpointTest : PostgresBootTest() {

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Autowired
    lateinit var dataSource: DataSource

    @LocalServerPort
    var port: Int = 0

    // HC5 request factory so the client can read 4xx bodies (JDK client cannot in streaming mode).
    private val client: RestTemplate by lazy {
        RestTemplate().apply {
            requestFactory = HttpComponentsClientHttpRequestFactory()
            setErrorHandler(object : ResponseErrorHandler {
                override fun hasError(response: ClientHttpResponse): Boolean = false
                override fun handleError(response: ClientHttpResponse) { /* return body as-is */ }
            })
        }
    }

    private fun url(path: String) = "http://localhost:$port$path"

    init {
        test("POST /v1/listings with a valid token creates an unverified listing owned by the authenticated account (round-trip)") {
            val (ownerId, bearer) = signupAndLogin("listing-happy@example.com")

            val resp = createListingWithCustomToken(bearer, validListingBody())

            resp.statusCode shouldBe HttpStatus.CREATED
            val body = resp.body!!
            body["name"] shouldBe "Halal Grill"
            body["address"] shouldBe "123 Main St"
            body["lat"].toString().toDouble() shouldBe 40.7128
            body["lng"].toString().toDouble() shouldBe -74.0060
            body["cuisine"] shouldBe "mediterranean"
            body["isHandCut"] shouldBe true
            body["halalScope"] shouldBe "NOT_DISCLOSED"
            body["verificationStatus"] shouldBe "UNVERIFIED"
            body["ownerId"].toString() shouldBe ownerId.toString()
            UUID.fromString(body["id"].toString()) shouldNotBe null

            // Persistence round-trip: read the row back from PostGIS.
            val row = JdbcTemplate(dataSource).queryForList(
                """
                SELECT name, address, cuisine, is_hand_cut, is_delivery, verification_status, owner_id,
                       ST_Y(location::geometry) AS lat, ST_X(location::geometry) AS lng
                FROM restaurant_listings WHERE id = ?
                """.trimIndent(),
                UUID.fromString(body["id"].toString()),
            ).single()
            row["name"] shouldBe "Halal Grill"
            row["address"] shouldBe "123 Main St"
            row["cuisine"] shouldBe "mediterranean"
            row["is_hand_cut"] shouldBe true
            row["is_delivery"] shouldBe true
            row["verification_status"] shouldBe "UNVERIFIED"
            row["owner_id"].toString() shouldBe ownerId.toString()
            (row["lat"] as Number).toDouble() shouldBe 40.7128
            (row["lng"] as Number).toDouble() shouldBe -74.0060
        }

        test("POST /v1/listings accepts an isDelivery flag and echoes it in the response (sc-184)") {
            val (_, bearer) = signupAndLogin("listing-delivery@example.com")

            val body = validListingBody().toMutableMap().apply { this["isDelivery"] = false }
            val resp = createListingWithCustomToken(bearer, body)

            resp.statusCode shouldBe HttpStatus.CREATED
            resp.body!!["isDelivery"] shouldBe false
        }

        test("POST /v1/listings defaults isDelivery to null (unknown) when omitted (sc-184)") {
            val (_, bearer) = signupAndLogin("listing-nodelivery@example.com")

            val body = validListingBody().toMutableMap().apply { this.remove("isDelivery") }
            val resp = createListingWithCustomToken(bearer, body)

            resp.statusCode shouldBe HttpStatus.CREATED
            resp.body!!.containsKey("isDelivery") shouldBe true
            resp.body!!["isDelivery"] shouldBe null
        }

        test("POST /v1/listings without a token returns a generic 401") {
            val resp = postJson("/v1/listings", validListingBody(), bearer = null)

            resp.statusCode shouldBe HttpStatus.UNAUTHORIZED
            resp.body!!["code"] shouldBe "unauthorized"
        }

        test("POST /v1/listings rejects a blank name with 400 invalid_input") {
            val (_, bearer) = signupAndLogin("listing-blank-name@example.com")

            val body = validListingBody().toMutableMap().apply { this["name"] = "   " }
            val resp = createListingWithCustomToken(bearer, body)

            resp.statusCode shouldBe HttpStatus.BAD_REQUEST
            resp.body!!["code"] shouldBe "invalid_input"
        }

        test("POST /v1/listings rejects an out-of-range latitude with 400 invalid_input") {
            val (_, bearer) = signupAndLogin("listing-bad-lat@example.com")

            val body = validListingBody().toMutableMap().apply { this["lat"] = 95.0 }
            val resp = createListingWithCustomToken(bearer, body)

            resp.statusCode shouldBe HttpStatus.BAD_REQUEST
            resp.body!!["code"] shouldBe "invalid_input"
        }

        test("POST /v1/listings with a token for a non-existent account returns 404 owner_not_found") {
            val minted = mint(role = "USER", sub = UUID.randomUUID().toString(), expSecondsAhead = 3600)

            val resp = createListingWithCustomToken(minted, validListingBody())

            resp.statusCode shouldBe HttpStatus.NOT_FOUND
            resp.body!!["code"] shouldBe "owner_not_found"
        }

        test("PATCH /v1/listings/{id} lets the owner edit own listing fields and echoes/persists them (sc-23/47/48)") {
            val (ownerId, bearer) = signupAndLogin("edit-owner@example.com")
            val created = createListingWithCustomToken(bearer, validListingBody())
            created.statusCode shouldBe HttpStatus.CREATED
            val listingId = UUID.fromString(created.body!!["id"].toString())

            val updateBody = linkedMapOf(
                "name" to "Halal Grill Renamed",
                "address" to "999 Renamed Ave",
                "lat" to 41.0,
                "lng" to -73.0,
                "cuisine" to "turkish",
                "isHandCut" to false,
                "isDelivery" to false,
                "halalScope" to "PARTIALLY_HALAL",
                "alcoholServed" to true,
            )
            val resp = patchJson("/v1/listings/$listingId", updateBody, bearer)

            resp.statusCode shouldBe HttpStatus.OK
            val body = resp.body!!
            body["id"].toString() shouldBe listingId.toString()
            body["name"] shouldBe "Halal Grill Renamed"
            body["address"] shouldBe "999 Renamed Ave"
            body["lat"].toString().toDouble() shouldBe 41.0
            body["lng"].toString().toDouble() shouldBe -73.0
            body["cuisine"] shouldBe "turkish"
            body["isHandCut"] shouldBe false
            body["isDelivery"] shouldBe false
            body["halalScope"] shouldBe "PARTIALLY_HALAL"
            body["verificationStatus"] shouldBe "UNVERIFIED"
            body["ownerId"].toString() shouldBe ownerId.toString()

            // Persistence round-trip: the source row reflects the edit.
            val row = JdbcTemplate(dataSource).queryForList(
                "SELECT name, address, cuisine, owner_id, verification_status FROM restaurant_listings WHERE id = ?",
                listingId,
            ).single()
            row["name"] shouldBe "Halal Grill Renamed"
            row["address"] shouldBe "999 Renamed Ave"
            row["cuisine"] shouldBe "turkish"
            row["owner_id"].toString() shouldBe ownerId.toString()
            row["verification_status"] shouldBe "UNVERIFIED"
        }

        test("PATCH /v1/listings/{id} is a full replace of editable fields: a null bool clears it, governance fields stay preserved") {
            val (_, bearer) = signupAndLogin("edit-null@example.com")
            val created = createListingWithCustomToken(bearer, validListingBody())
            val listingId = UUID.fromString(created.body!!["id"].toString())

            // Full editable payload; isDelivery=null (unknown/pickup-baseline) explicitly
            // clears the earlier true claim. Governance fields (verification_status, owner_id) are
            // never touched by an edit.
            val updateBody = linkedMapOf(
                "name" to "Full Replace Grill",
                "address" to "123 Main St",
                "lat" to 40.7128,
                "lng" to -74.0060,
                "cuisine" to "mediterranean",
                "isHandCut" to true,
                "isDelivery" to null,
            )
            val resp = patchJson("/v1/listings/$listingId", updateBody, bearer)

            resp.statusCode shouldBe HttpStatus.OK
            val row = JdbcTemplate(dataSource).queryForList(
                "SELECT name, is_delivery, verification_status, owner_id FROM restaurant_listings WHERE id = ?",
                listingId,
            ).single()
            row["name"] shouldBe "Full Replace Grill"
            row["is_delivery"] shouldBe null
            row["verification_status"] shouldBe "UNVERIFIED"
            row["owner_id"] shouldNotBe null
        }

        test("PATCH /v1/listings/{id} by a non-owner returns 403 not_listing_owner") {
            val (ownerId, ownerBearer) = signupAndLogin("edit-owner-b@example.com")
            val created = createListingWithCustomToken(ownerBearer, validListingBody())
            val listingId = UUID.fromString(created.body!!["id"].toString())

            // A different account edits -> 403, and the row is untouched.
            val (_, strangerBearer) = signupAndLogin("edit-stranger@example.com")
            val updateBody = linkedMapOf(
                "name" to "Hijack Grill", "address" to "123 Main St", "lat" to 40.7128,
                "lng" to -74.0060, "cuisine" to "mediterranean", "isHandCut" to true,
            )
            val resp = patchJson("/v1/listings/$listingId", updateBody, strangerBearer)

            resp.statusCode shouldBe HttpStatus.FORBIDDEN
            resp.body!!["code"] shouldBe "not_listing_owner"

            val row = JdbcTemplate(dataSource).queryForList(
                "SELECT name, owner_id FROM restaurant_listings WHERE id = ?",
                listingId,
            ).single()
            row["name"] shouldBe "Halal Grill"
            row["owner_id"].toString() shouldBe ownerId.toString()
        }

        test("PATCH /v1/listings/{id} for an unknown listing returns 404 listing_not_found") {
            val (_, bearer) = signupAndLogin("edit-ghost@example.com")
            val updateBody = linkedMapOf(
                "name" to "Ghost Grill", "address" to "123 Main St", "lat" to 40.7128,
                "lng" to -74.0060, "cuisine" to "mediterranean", "isHandCut" to true,
            )
            val resp = patchJson("/v1/listings/${UUID.randomUUID()}", updateBody, bearer)

            resp.statusCode shouldBe HttpStatus.NOT_FOUND
            resp.body!!["code"] shouldBe "listing_not_found"
        }

        test("PATCH /v1/listings/{id} without a token returns a generic 401") {
            val updateBody = linkedMapOf(
                "name" to "X", "address" to "1 St", "lat" to 1.0, "lng" to 2.0,
                "cuisine" to "x", "isHandCut" to null,
            )
            val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
            val resp = client.exchange(
                url("/v1/listings/${UUID.randomUUID()}"), HttpMethod.PATCH,
                HttpEntity<Any>(updateBody, headers), Map::class.java,
            )
            resp.statusCode shouldBe HttpStatus.UNAUTHORIZED
            resp.body!!["code"] shouldBe "unauthorized"
        }

        test("PATCH /v1/listings/{id} rejects a blank name with 400 invalid_input") {
            val (_, bearer) = signupAndLogin("edit-blank@example.com")
            val created = createListingWithCustomToken(bearer, validListingBody())
            val listingId = UUID.fromString(created.body!!["id"].toString())

            val updateBody = linkedMapOf(
                "name" to "   ", "address" to "123 Main St", "lat" to 40.7128,
                "lng" to -74.0060, "cuisine" to "mediterranean", "isHandCut" to true,
            )
            val resp = patchJson("/v1/listings/$listingId", updateBody, bearer)

            resp.statusCode shouldBe HttpStatus.BAD_REQUEST
            resp.body!!["code"] shouldBe "invalid_input"
        }
    }

    private fun createListingWithCustomToken(bearer: String, body: Map<String, Any?>): ResponseEntity<Map<*, *>> {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            setBearerAuth(bearer)
        }
        return client.exchange(url("/v1/listings"), HttpMethod.POST, HttpEntity<Any>(body, headers), Map::class.java)
    }

    private fun validListingBody(): Map<String, Any?> = linkedMapOf(
        "name" to "Halal Grill",
        "address" to "123 Main St",
        "lat" to 40.7128,
        "lng" to -74.0060,
        "cuisine" to "mediterranean",
        "isHandCut" to true,
        "isDelivery" to true,
    )

    private fun postJson(path: String, body: Any, bearer: String?): ResponseEntity<Map<*, *>> {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        if (bearer != null) headers.setBearerAuth(bearer)
        return client.exchange(url(path), HttpMethod.POST, HttpEntity<Any>(body, headers), Map::class.java)
    }

    private fun patchJson(path: String, body: Any, bearer: String?): ResponseEntity<Map<*, *>> {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        if (bearer != null) headers.setBearerAuth(bearer)
        return client.exchange(url(path), HttpMethod.PATCH, HttpEntity<Any>(body, headers), Map::class.java)
    }

    /** Signs up a fresh account and returns its id and a real login access token. */
    private fun signupAndLogin(email: String): Pair<UUID, String> {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val signup = restTemplate.postForEntity(
            "/v1/auth/signup",
            HttpEntity(mapOf("email" to email, "password" to "s3cr3t-password"), headers),
            Map::class.java,
        )
        signup.statusCode shouldBe HttpStatus.CREATED

        val login = client.postForEntity(
            url("/v1/auth/login"),
            HttpEntity(mapOf("email" to email, "password" to "s3cr3t-password"), headers),
            Map::class.java,
        )
        login.statusCode shouldBe HttpStatus.OK
        return UUID.fromString(signup.body!!["id"].toString()) to login.body!!["accessToken"].toString()
    }

    /** Mints and RS256-signs a token with the same test private key the app boots with. */
    private fun mint(
        role: String?,
        sub: String,
        expSecondsAhead: Long,
    ): String {
        val builder = JWTClaimsSet.Builder()
            .subject(sub)
            .issuer("halal-food-finder")
            .issueTime(Date.from(Instant.now()))
            .expirationTime(Date.from(Instant.now().plusSeconds(expSecondsAhead)))
        if (role != null) builder.claim("role", role)

        val signed = SignedJWT(
            JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).build(),
            builder.build(),
        )
        signed.sign(RSASSASigner(testPrivateKey()))
        return signed.serialize()
    }

    private fun testPrivateKey(): RSAPrivateKey {
        val der = Base64.getDecoder().decode(TEST_PRIVATE_KEY_B64)
        val factory = KeyFactory.getInstance("RSA")
        return factory.generatePrivate(PKCS8EncodedKeySpec(der)) as RSAPrivateKey
    }

    /** Point the app at the fixed test RSA key so issuer and verifier share the pair. */
    @TestConfiguration
    class JwtKeyInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
        override fun initialize(context: ConfigurableApplicationContext) {
            TestPropertyValues.of(
                "app.jwt.rsa-private-key-base64=$TEST_PRIVATE_KEY_B64",
                "app.jwt.issuer=halal-food-finder",
            ).applyTo(context.environment)
        }
    }

    companion object {
        /** Base64 PKCS#8 DER of the test RSA key (fixed at test time). */
        private val TEST_PRIVATE_KEY_B64: String by lazy {
            ClassPathResource("test-jwt-rsa-private.pem").inputStream.bufferedReader().use { it.readText() }.trim()
        }
    }
}
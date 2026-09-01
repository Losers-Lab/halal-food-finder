package com.tahirslist.bootstrap.verification

import com.tahirslist.bootstrap.PostgresBootTest
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
import org.springframework.core.io.ByteArrayResource
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
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.ResponseErrorHandler
import org.springframework.web.client.RestTemplate
import java.util.UUID

/**
 * Owner-claim HTTP surface (sc-46): POST /v1/listings/{id}/claim.
 *
 * End-to-end against the full application graph (controller -> ClaimListing ->
 * RequestVerification(DeferToHuman) -> JdbcHalalCertificationReviewRepository ->
 * real Flyway V13). Covers: 201 happy path persisting an AI_SUGGESTED review,
 * unauthenticated 401, the owner-only guard (403), 404 for an unknown listing,
 * and 400 for a blank proof.
 *
 * The test env has no `app.verification.hosted.endpoint`, so the safe
 * DeferToHumanProvider is active — every claim is suggested NEEDS_REVIEW and the
 * review sits in AI_SUGGESTED awaiting the human (sc-73).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = [VerificationClaimEndpointTest.JwtKeyInitializer::class])
class VerificationClaimEndpointTest : PostgresBootTest() {

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @LocalServerPort
    var port: Int = 0

    private val client: RestTemplate by lazy {
        RestTemplate().apply {
            requestFactory = HttpComponentsClientHttpRequestFactory()
            setErrorHandler(object : ResponseErrorHandler {
                override fun hasError(response: ClientHttpResponse): Boolean = false
                override fun handleError(response: ClientHttpResponse) { /* return body as-is */ }
            })
        }
    }

    private val certBytes = byteArrayOf(1, 2, 3, 4)

    private fun reviewCount(): Int = jdbc.queryForObject(
        "SELECT count(*) FROM halal_certification_reviews",
        Int::class.java,
    )!!

    private fun url(path: String) = "http://localhost:$port$path"

    init {
        test("owner claims a listing: 201, drives review to AI_SUGGESTED, persists it") {
            val (ownerId, bearer) = signupAndLogin("claim-happy@example.com")
            val listingId = createListing(bearer, ownerId)

            val resp = claim(listingId, " I own this; license TAH-2024-118. ", certBytes, bearer)

            resp.statusCode shouldBe HttpStatus.CREATED
            val json = resp.body ?: error("null body")
            val reviewId = UUID.fromString(json["reviewId"].toString())
            json["listingId"].toString() shouldBe listingId.toString()
            json["state"] shouldBe "AI_SUGGESTED"
            json["suggestedVerdict"] shouldBe "NEEDS_REVIEW"

            val row = jdbc.query(
                """
                SELECT state, suggestion_verdict, submitted_by, listing_id, ai_consent_at
                FROM halal_certification_reviews WHERE id = ?
                """.trimIndent(),
                { rs, _ -> arrayOf(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getTimestamp(5)?.toInstant()?.toString()) },
                reviewId,
            ).first()
            row[0] shouldBe "AI_SUGGESTED"
            row[1] shouldBe "NEEDS_REVIEW"
            row[2] shouldBe ownerId.toString()
            row[3] shouldBe listingId.toString()
            // sc-120: consent is recorded with the verification request
            row[4] shouldNotBe null
        }

        test("a claim without explicit AI-analysis consent returns 400 and stores nothing") {
            val (ownerId, bearer) = signupAndLogin("claim-noconsent@example.com")
            val listingId = createListing(bearer, ownerId)
            val before = reviewCount()

            val resp = claim(listingId, "license", certBytes, bearer, aiConsent = "false")

            resp.statusCode shouldBe HttpStatus.BAD_REQUEST
            codeOf(resp) shouldBe "invalid_input"
            reviewCount() shouldBe before
        }

        test("a claim never auto-verifies the listing") {
            val (ownerId, bearer) = signupAndLogin("claim-safe@example.com")
            val listingId = createListing(bearer, ownerId)

            val resp = claim(listingId, "license", certBytes, bearer)

            resp.statusCode shouldBe HttpStatus.CREATED
            resp.body!!["state"] shouldBe "AI_SUGGESTED"
            val listingStatus = jdbc.queryForObject(
                "SELECT verification_status FROM restaurant_listings WHERE id = ?",
                String::class.java,
                listingId,
            )
            listingStatus shouldBe "UNVERIFIED"
        }

        test("unauthenticated call returns a generic 401") {
            val resp = claim(UUID.randomUUID(), "license", certBytes, null)
            resp.statusCode shouldBe HttpStatus.UNAUTHORIZED
            codeOf(resp) shouldBe "unauthorized"
        }

        test("a non-owner claiming a listing returns 403 not_listing_owner") {
            val (ownerId, bearerOwner) = signupAndLogin("claim-owner@example.com")
            val listingId = createListing(bearerOwner, ownerId)
            val (_, bearerIntruder) = signupAndLogin("claim-intruder@example.com")

            val resp = claim(listingId, "I own this", certBytes, bearerIntruder)

            resp.statusCode shouldBe HttpStatus.FORBIDDEN
            codeOf(resp) shouldBe "not_listing_owner"
        }

        test("claiming an unknown listing returns 404 listing_not_found") {
            val (_, bearer) = signupAndLogin("claim-404@example.com")
            val phantom = UUID.randomUUID()

            val resp = claim(phantom, "license", certBytes, bearer)

            resp.statusCode shouldBe HttpStatus.NOT_FOUND
            codeOf(resp) shouldBe "listing_not_found"
        }

        test("blank proof returns 400 invalid_input") {
            val (ownerId, bearer) = signupAndLogin("claim-blank@example.com")
            val listingId = createListing(bearer, ownerId)

            val resp = claim(listingId, "   ", certBytes, bearer)

            resp.statusCode shouldBe HttpStatus.BAD_REQUEST
            codeOf(resp) shouldBe "invalid_input"
        }
    }

    private fun claim(listingId: UUID, proof: String, imageBytes: ByteArray, bearer: String?): ResponseEntity<Map<*, *>> =
        claim(listingId, proof, imageBytes, bearer, aiConsent = "true")

    private fun claim(
        listingId: UUID,
        proof: String,
        imageBytes: ByteArray,
        bearer: String?,
        aiConsent: String,
    ): ResponseEntity<Map<*, *>> {
        val headers = HttpHeaders()
        headers.contentType = MediaType.MULTIPART_FORM_DATA
        if (bearer != null) headers.setBearerAuth(bearer)
        val body = LinkedMultiValueMap<String, Any>()
        body.add("proof", proof)
        body.add("aiConsent", aiConsent)
        body.add("certImage", UploadResource(imageBytes, "cert.jpg"))
        return client.exchange(
            url("/v1/listings/$listingId/claim"),
            HttpMethod.POST,
            HttpEntity<Any>(body, headers),
            Map::class.java,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun codeOf(resp: ResponseEntity<Map<*, *>>): String? =
        resp.body?.let { it["code"] as? String }

    private fun createListing(bearer: String, ownerId: UUID): UUID {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON; setBearerAuth(bearer) }
        val body = linkedMapOf(
            "name" to "Halal Grill",
            "address" to "123 Main St",
            "lat" to 40.7128,
            "lng" to -74.0060,
            "cuisine" to "mediterranean",
            "cuttingMethod" to "HAND_CUT",
        )
        val resp = client.exchange(url("/v1/listings"), HttpMethod.POST, HttpEntity<Any>(body, headers), Map::class.java)
        resp.statusCode shouldBe HttpStatus.CREATED
        resp.body!!["ownerId"].toString() shouldBe ownerId.toString()
        return UUID.fromString(resp.body!!["id"].toString())
    }

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
        private val TEST_PRIVATE_KEY_B64: String by lazy {
            ClassPathResource("test-jwt-rsa-private.pem").inputStream.bufferedReader().use { it.readText() }.trim()
        }
    }
}

/** A [ByteArrayResource] that reports a filename, so the multipart encoder sends it as a file part. */
private class UploadResource(content: ByteArray, private val filename: String) : ByteArrayResource(content) {
    override fun getFilename(): String = filename
}
package com.tahirslist.bootstrap.verification

import com.tahirslist.bootstrap.PostgresBootTest
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
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
 * Verification Committee HTTP surface (sc-73): the human review loop.
 *
 * End-to-end against the full application graph (controller -> VerificationCommittee
 * -> JdbcHalalCertificationReviewRepository -> JdbcRestaurantListingRepository ->
 * real Flyway V13 + V15). Covers:
 *   - a VC member lists the pending (AI_SUGGESTED) workqueue;
 *   - approve promotes the listing to VERIFIED (source + read mirror);
 *   - deny records the reason and leaves the listing UNVERIFIED;
 *   - re-deciding an already-decided review is a 409 conflict;
 *   - RBAC: a non-committee (USER) caller is forbidden, and unauthenticated is 401.
 *
 * A VC token is obtained by promoting a just-signed-up account's role in the DB
 * and logging back in (the issuer reads the role from the stored account), which
 * exercises the real JWT role claim rather than hand-minting a token.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = [VerificationCommitteeEndpointTest.JwtKeyInitializer::class])
class VerificationCommitteeEndpointTest : PostgresBootTest() {

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

    private fun url(path: String) = "http://localhost:$port$path"

    init {
        test("a Verification Committee member lists pending AI_SUGGESTED reviews") {
            val owner = signupAndLogin("vc-list-owner@example.com")
            val listingId = createListing(owner.bearer, owner.id)
            val reviewId = claimAndGetReviewId(listingId, owner.bearer)
            val vc = signupPromoteLogin("vc-list-member@example.com")

            val resp = client.exchange(
                url("/v1/verification-committee/reviews"),
                HttpMethod.GET,
                authed(vc.bearer),
                List::class.java,
            )

            resp.statusCode shouldBe HttpStatus.OK
            @Suppress("UNCHECKED_CAST")
            val reviews = resp.body as List<Map<String, Any>>
            reviews.map { it["reviewId"].toString() } shouldContain reviewId.toString()
            val thisOne = reviews.first { it["reviewId"].toString() == reviewId.toString() }
            thisOne["listingId"].toString() shouldBe listingId.toString()
            thisOne["state"] shouldBe "AI_SUGGESTED"
            thisOne["suggestedVerdict"] shouldBe "NEEDS_REVIEW"
            thisOne["decisionOutcome"] shouldBe null
        }

        test("approve promotes the listing to VERIFIED in source and read mirror") {
            val owner = signupAndLogin("vc-approve-owner@example.com")
            val listingId = createListing(owner.bearer, owner.id)
            val reviewId = claimAndGetReviewId(listingId, owner.bearer)
            val vc = signupPromoteLogin("vc-approve-member@example.com")

            val resp = client.exchange(
                url("/v1/verification-committee/reviews/$reviewId/approve"),
                HttpMethod.POST,
                authed(vc.bearer, body = mapOf("reason" to "cert matches listing")),
                Map::class.java,
            )

            resp.statusCode shouldBe HttpStatus.OK
            resp.body!!["state"] shouldBe "APPROVED"
            resp.body!!["decisionOutcome"] shouldBe "APPROVED"
            resp.body!!["decisionReason"] shouldBe "cert matches listing"

            val listingStatus = jdbc.queryForObject(
                "SELECT verification_status FROM restaurant_listings WHERE id = ?",
                String::class.java,
                listingId,
            )
            listingStatus shouldBe "VERIFIED"
            val mirrorStatus = jdbc.queryForObject(
                "SELECT verification_status FROM listing_search WHERE id = ?",
                String::class.java,
                listingId,
            )
            mirrorStatus shouldBe "VERIFIED"
        }

        test("deny records the reason and leaves the listing UNVERIFIED") {
            val owner = signupAndLogin("vc-deny-owner@example.com")
            val listingId = createListing(owner.bearer, owner.id)
            val reviewId = claimAndGetReviewId(listingId, owner.bearer)
            val vc = signupPromoteLogin("vc-deny-member@example.com")

            val resp = client.exchange(
                url("/v1/verification-committee/reviews/$reviewId/deny"),
                HttpMethod.POST,
                authed(vc.bearer, body = mapOf("reason" to "cert image unreadable")),
                Map::class.java,
            )

            resp.statusCode shouldBe HttpStatus.OK
            resp.body!!["state"] shouldBe "DENIED"
            resp.body!!["decisionOutcome"] shouldBe "DENIED"
            resp.body!!["decisionReason"] shouldBe "cert image unreadable"

            val listingStatus = jdbc.queryForObject(
                "SELECT verification_status FROM restaurant_listings WHERE id = ?",
                String::class.java,
                listingId,
            )
            listingStatus shouldBe "UNVERIFIED"
        }

        test("deciding an already-decided review is a 409 conflict") {
            val owner = signupAndLogin("vc-twice-owner@example.com")
            val listingId = createListing(owner.bearer, owner.id)
            val reviewId = claimAndGetReviewId(listingId, owner.bearer)
            val vc = signupPromoteLogin("vc-twice-member@example.com")

            client.exchange(
                url("/v1/verification-committee/reviews/$reviewId/deny"),
                HttpMethod.POST,
                authed(vc.bearer, body = mapOf("reason" to "first")),
                Map::class.java,
            )
            val resp = client.exchange(
                url("/v1/verification-committee/reviews/$reviewId/approve"),
                HttpMethod.POST,
                authed(vc.bearer, body = mapOf("reason" to "second")),
                Map::class.java,
            )

            resp.statusCode shouldBe HttpStatus.CONFLICT
            codeOf(resp) shouldBe "review_not_pending"
        }

        test("a non-committee (USER) caller is forbidden from the workqueue") {
            val owner = signupAndLogin("vc-forbidden-owner@example.com")
            val listingId = createListing(owner.bearer, owner.id)
            claimAndGetReviewId(listingId, owner.bearer)

            val resp = client.exchange(
                url("/v1/verification-committee/reviews"),
                HttpMethod.GET,
                authed(owner.bearer),
                Map::class.java,
            )

            resp.statusCode shouldBe HttpStatus.FORBIDDEN
            codeOf(resp) shouldBe "forbidden"
        }

        test("unauthenticated calls are a generic 401") {
            val resp = client.exchange(
                url("/v1/verification-committee/reviews"),
                HttpMethod.GET,
                authed(null),
                Map::class.java,
            )
            resp.statusCode shouldBe HttpStatus.UNAUTHORIZED
            codeOf(resp) shouldBe "unauthorized"
        }
    }

    /* ---- helpers ---- */

    private data class Session(val id: UUID, val bearer: String)

    private fun signupAndLogin(email: String): Session {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val signup = restTemplate.postForEntity(
            "/v1/auth/signup",
            HttpEntity(mapOf("email" to email, "password" to "s3cr3t-password"), headers),
            Map::class.java,
        )
        signup.statusCode shouldBe HttpStatus.CREATED
        val bearer = login(email)
        return Session(UUID.fromString(signup.body!!["id"].toString()), bearer)
    }

    /** Promote the account to VERIFICATION_COMMITTEE in the DB, then mint a token. */
    private fun signupPromoteLogin(email: String): Session {
        val session = signupAndLogin(email)
        jdbc.update("UPDATE users SET role = 'VERIFICATION_COMMITTEE' WHERE id = ?", session.id)
        return Session(session.id, login(email))
    }

    private fun login(email: String): String {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val resp = client.postForEntity(
            url("/v1/auth/login"),
            HttpEntity(mapOf("email" to email, "password" to "s3cr3t-password"), headers),
            Map::class.java,
        )
        resp.statusCode shouldBe HttpStatus.OK
        return resp.body!!["accessToken"].toString()
    }

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

    private fun claimAndGetReviewId(listingId: UUID, bearer: String): UUID {
        val headers = HttpHeaders().apply { contentType = MediaType.MULTIPART_FORM_DATA; setBearerAuth(bearer) }
        val parts = LinkedMultiValueMap<String, Any>()
        parts.add("proof", " I own this; license TAH-2024-118. ")
        parts.add("aiConsent", "true")
        parts.add("certImage", CertImageResource(certBytes, "cert.jpg"))
        val resp = client.exchange(
            url("/v1/listings/$listingId/claim"),
            HttpMethod.POST,
            HttpEntity<Any>(parts, headers),
            Map::class.java,
        )
        resp.statusCode shouldBe HttpStatus.CREATED
        return UUID.fromString(resp.body!!["reviewId"].toString())
    }

    private fun authed(bearer: String?, body: Any? = null): HttpEntity<Any?> {
        val headers = HttpHeaders()
        if (bearer != null) headers.setBearerAuth(bearer)
        headers.contentType = MediaType.APPLICATION_JSON
        return HttpEntity(body, headers)
    }

    @Suppress("UNCHECKED_CAST")
    private fun codeOf(resp: ResponseEntity<Map<*, *>>): String? =
        resp.body?.let { it["code"] as? String }

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
private class CertImageResource(content: ByteArray, private val filename: String) : ByteArrayResource(content) {
    override fun getFilename(): String = filename
}
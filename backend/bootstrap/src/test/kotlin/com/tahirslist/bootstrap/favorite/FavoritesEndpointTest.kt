package com.tahirslist.bootstrap.favorite

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
import org.springframework.test.context.ContextConfiguration
import org.springframework.web.client.ResponseErrorHandler
import org.springframework.web.client.RestTemplate
import com.tahirslist.bootstrap.PostgresBootTest
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.UUID

/**
 * Favourites HTTP surface (sc-50/51/52): POST/DELETE/GET /v1/favorites.
 *
 * End-to-end against the full application graph (controller -> favorites use
 * cases -> JdbcFavoritesRepository -> real Flyway V11). Covers the mandated
 * cases: the idempotent favourite/unfavourite contract, the browse-card read
 * shape, unauthenticated 401, expired/tampered-token 401 (Omar's R1 — the first
 * protected endpoint), and a 404 for a non-existent listing.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = [FavoritesEndpointTest.JwtKeyInitializer::class])
class FavoritesEndpointTest : PostgresBootTest() {

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @LocalServerPort
    var port: Int = 0

    // HC5 request factory so the client can read 401/404/400 bodies.
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

        test("POST /v1/favorites/{id} favourites a listing (204) and is idempotent") {
            val (ownerId, bearer) = signupAndLogin("fav-happy@example.com")
            val listingId = createListing(bearer, ownerId)

            val first = favorite(listingId, bearer)
            first.statusCode shouldBe HttpStatus.NO_CONTENT

            val second = favorite(listingId, bearer) // duplicate — no-op
            second.statusCode shouldBe HttpStatus.NO_CONTENT

            listFavorites(bearer).map { it["id"].toString() } shouldBe listOf(listingId.toString())
        }

        test("DELETE /v1/favorites/{id} unfavourites (204) and is idempotent") {
            val (ownerId, bearer) = signupAndLogin("fav-unfav@example.com")
            val listingId = createListing(bearer, ownerId)
            favorite(listingId, bearer).statusCode shouldBe HttpStatus.NO_CONTENT

            val first = unfavorite(listingId, bearer)
            first.statusCode shouldBe HttpStatus.NO_CONTENT
            listFavorites(bearer) shouldBe emptyList()

            val second = unfavorite(listingId, bearer) // already gone — no-op
            second.statusCode shouldBe HttpStatus.NO_CONTENT
            listFavorites(bearer) shouldBe emptyList()
        }

        test("GET /v1/favorites returns the user's favourites as browse-card objects identical to /v1/listings shape") {
            val (ownerId, bearer) = signupAndLogin("fav-card@example.com")
            val listingId = createListing(bearer, ownerId)
            favorite(listingId, bearer).statusCode shouldBe HttpStatus.NO_CONTENT

            val cards = listFavorites(bearer)

            cards.size shouldBe 1
            val card = cards.single()
            card["id"].toString() shouldBe listingId.toString()
            card["name"] shouldBe "Halal Grill"
            card["address"] shouldBe "123 Main St"
            card["lat"].toString().toDouble() shouldBe 40.7128
            card["lng"].toString().toDouble() shouldBe -74.0060
            card["cuisine"] shouldBe "mediterranean"
            card["cuttingMethod"] shouldBe "HAND_CUT"
            card["verificationStatus"] shouldBe "UNVERIFIED"
            card["imageThumbnailUrl"].toString() shouldNotBe ""
        }

        test("POST /v1/favorites/{id} without a token returns a generic 401") {
            val resp = favorite(UUID.randomUUID(), bearer = null)

            resp.statusCode shouldBe HttpStatus.UNAUTHORIZED
            codeOf(resp) shouldBe "unauthorized"
        }

        test("GET /v1/favorites without a token returns a generic 401") {
            val resp = getFavorites(bearer = null)

            resp.statusCode shouldBe HttpStatus.UNAUTHORIZED
            codeOf(resp) shouldBe "unauthorized"
        }

        test("DELETE /v1/favorites/{id} without a token returns a generic 401") {
            val resp = unfavorite(UUID.randomUUID(), bearer = null)

            resp.statusCode shouldBe HttpStatus.UNAUTHORIZED
            codeOf(resp) shouldBe "unauthorized"
        }

        test("an expired access token is rejected with a generic 401 on the favourites surface (Omar R1)") {
            val expired = mint(role = "USER", sub = UUID.randomUUID().toString(), expSecondsAhead = -60)

            val resp = getFavorites(bearer = expired)

            resp.statusCode shouldBe HttpStatus.UNAUTHORIZED
            codeOf(resp) shouldBe "unauthorized"
        }

        test("a tampered access token is rejected with a generic 401 on the favourites surface (Omar R1)") {
            val minted = mint(role = "USER", sub = UUID.randomUUID().toString(), expSecondsAhead = 3600)
            val tampered = tamper(minted)

            val resp = getFavorites(bearer = tampered)

            resp.statusCode shouldBe HttpStatus.UNAUTHORIZED
            codeOf(resp) shouldBe "unauthorized"
        }

        test("POST /v1/favorites/{id} for a non-existent listing returns 404 listing_not_found") {
            val (_, bearer) = signupAndLogin("fav-404@example.com")
            val phantom = UUID.randomUUID()

            val resp = favorite(phantom, bearer)

            resp.statusCode shouldBe HttpStatus.NOT_FOUND
            codeOf(resp) shouldBe "listing_not_found"
        }

        test("favourites are scoped to the authenticated user, not leaked") {
            val (ownerA, bearerA) = signupAndLogin("fav-user-a@example.com")
            val listingA = createListing(bearerA, ownerA)
            favorite(listingA, bearerA).statusCode shouldBe HttpStatus.NO_CONTENT

            val (_, bearerB) = signupAndLogin("fav-user-b@example.com")
            listFavorites(bearerB) shouldBe emptyList()

            listFavorites(bearerA).map { it["id"].toString() } shouldBe listOf(listingA.toString())
        }
    }

    private fun favorite(listingId: UUID, bearer: String?): ResponseEntity<String> =
        sendRaw(HttpMethod.POST, "/v1/favorites/$listingId", bearer, body = null)

    private fun unfavorite(listingId: UUID, bearer: String?): ResponseEntity<String> =
        sendRaw(HttpMethod.DELETE, "/v1/favorites/$listingId", bearer, body = null)

    /** Returns the `code` field of a JSON error envelope, or null if the body has none. */
    private fun codeOf(resp: ResponseEntity<String>): String? =
        resp.body
            ?.let { json -> if (json.isBlank()) null else mapper.readValue(json, Map::class.java) }
            ?.let { it["code"] as? String }

    private fun listFavorites(bearer: String?): List<Map<*, *>> {
        val resp = getFavorites(bearer)
        resp.statusCode shouldBe HttpStatus.OK
        val json = resp.body ?: return emptyList()
        @Suppress("UNCHECKED_CAST")
        return (mapper.readValue(json, List::class.java) as List<*>).map { it as Map<*, *> }
    }

    private fun getFavorites(bearer: String?): ResponseEntity<String> =
        sendRaw(HttpMethod.GET, "/v1/favorites", bearer, body = null)

    private fun sendRaw(method: HttpMethod, path: String, bearer: String?, body: Map<String, Any?>?): ResponseEntity<String> {
        val headers = HttpHeaders()
        if (bearer != null) headers.setBearerAuth(bearer)
        return client.exchange(url(path), method, HttpEntity<Any?>(body, headers), String::class.java)
    }

    /** Shared Jackson mapper used only to parse test assertion bodies. */
    private val mapper: com.fasterxml.jackson.databind.ObjectMapper = com.fasterxml.jackson.databind.ObjectMapper()

    private fun createListing(bearer: String, ownerId: UUID): UUID {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            setBearerAuth(bearer)
        }
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

    private fun mint(role: String?, sub: String, expSecondsAhead: Long): String {
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

    private fun tamper(jwt: String): String {
        val parts = jwt.split(".")
        require(parts.size == 3) { "expected a compact JWT" }
        val sig = parts[2].toCharArray()
        val flipped = if (sig[0] == 'A') 'B' else 'A'
        sig[0] = flipped
        return "${parts[0]}.${parts[1]}.${String(sig)}"
    }

    private fun testPrivateKey(): RSAPrivateKey {
        val der = Base64.getDecoder().decode(TEST_PRIVATE_KEY_B64)
        val factory = KeyFactory.getInstance("RSA")
        return factory.generatePrivate(PKCS8EncodedKeySpec(der)) as RSAPrivateKey
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
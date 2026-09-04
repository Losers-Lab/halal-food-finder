package com.tahirslist.bootstrap

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
import org.springframework.test.context.ContextConfiguration
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.ResponseErrorHandler
import org.springframework.web.client.RestTemplate
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.imageio.ImageIO

/**
 * Owner image-management HTTP surface (sc-53/54): PUT/DELETE /v1/listings/{id}/image.
 *
 * End-to-end against the full application graph (controller -> Add/RemoveListingImage
 * use cases -> InMemoryImagePort in the test env). Covers: 204 happy upload with a
 * read-back round-trip (GET the stored FULL variant), 204 idempotent re-upload / remove,
 * the owner-only guard (403), 404 for an unknown listing, unauthenticated 401, a missing
 * `image` part -> 400, and an undecodable upload -> 400.
 *
 * The test env has no `app.storage.s3.endpoint`, so ImageInfraConfig supplies the
 * InMemoryImagePort bean — the same port the application tests pin, and the S3 adapter
 * implements the identical contract (verified in S3ImagePortContractTest).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = [ListingImageEndpointTest.JwtKeyInitializer::class])
class ListingImageEndpointTest : PostgresBootTest() {

    @Autowired
    lateinit var restTemplate: TestRestTemplate

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
        test("owner uploads a hero image: 204, then the FULL variant is served back (sc-53)") {
            val (ownerId, bearer) = signupAndLogin("img-put-happy@example.com")
            val listingId = createListing(bearer, ownerId)

            val put = putImage(listingId, jpegBytes(), "image/jpeg", bearer)
            put.statusCode shouldBe HttpStatus.NO_CONTENT

            // Read-back round-trip through the sc-157 GET image endpoint.
            val fetched = getImage(listingId, "full", bearer)
            fetched.statusCode shouldBe HttpStatus.OK
        }

        test("re-uploading after a previous image still succeeds (last-write-wins replace)") {
            val (ownerId, bearer) = signupAndLogin("img-put-again@example.com")
            val listingId = createListing(bearer, ownerId)

            putImage(listingId, jpegBytes(), "image/jpeg", bearer).statusCode shouldBe HttpStatus.NO_CONTENT
            putImage(listingId, jpegBytes(), "image/jpeg", bearer).statusCode shouldBe HttpStatus.NO_CONTENT

            getImage(listingId, "full", bearer).statusCode shouldBe HttpStatus.OK
        }

        test("owner removes the listing image: 204, and the variant is gone (sc-54)") {
            val (ownerId, bearer) = signupAndLogin("img-del-happy@example.com")
            val listingId = createListing(bearer, ownerId)
            putImage(listingId, jpegBytes(), "image/jpeg", bearer).statusCode shouldBe HttpStatus.NO_CONTENT

            val del = deleteImage(listingId, bearer)
            del.statusCode shouldBe HttpStatus.NO_CONTENT

            getImage(listingId, "full", bearer).statusCode shouldBe HttpStatus.NOT_FOUND
        }

        test("removing a listing with no stored image is a silent 204, not an error (idempotent)") {
            val (ownerId, bearer) = signupAndLogin("img-del-idem@example.com")
            val listingId = createListing(bearer, ownerId)

            deleteImage(listingId, bearer).statusCode shouldBe HttpStatus.NO_CONTENT
            deleteImage(listingId, bearer).statusCode shouldBe HttpStatus.NO_CONTENT
        }

        test("a non-owner upload is forbidden (403) and stores nothing") {
            val (ownerId, ownerBearer) = signupAndLogin("img-owner@example.com")
            val listingId = createListing(ownerBearer, ownerId)
            val (_, strangerBearer) = signupAndLogin("img-stranger@example.com")

            val resp = putImage(listingId, jpegBytes(), "image/jpeg", strangerBearer)

            resp.statusCode shouldBe HttpStatus.FORBIDDEN
            codeOf(resp) shouldBe "not_listing_owner"
            // The stranger could not store anything: the variant stays absent.
            getImage(listingId, "full", strangerBearer).statusCode shouldBe HttpStatus.NOT_FOUND
        }

        test("a non-owner remove is forbidden (403)") {
            val (ownerId, ownerBearer) = signupAndLogin("img-del-owner@example.com")
            val listingId = createListing(ownerBearer, ownerId)
            putImage(listingId, jpegBytes(), "image/jpeg", ownerBearer).statusCode shouldBe HttpStatus.NO_CONTENT
            val (_, strangerBearer) = signupAndLogin("img-del-stranger@example.com")

            val resp = deleteImage(listingId, strangerBearer)

            resp.statusCode shouldBe HttpStatus.FORBIDDEN
            codeOf(resp) shouldBe "not_listing_owner"
            getImage(listingId, "full", ownerBearer).statusCode shouldBe HttpStatus.OK // untouched
        }

        test("uploading to an unknown listing returns 404 listing_not_found") {
            val (_, bearer) = signupAndLogin("img-put-404@example.com")

            val resp = putImage(UUID.randomUUID(), jpegBytes(), "image/jpeg", bearer)

            resp.statusCode shouldBe HttpStatus.NOT_FOUND
            codeOf(resp) shouldBe "listing_not_found"
        }

        test("removing an unknown listing's image returns 404 listing_not_found") {
            val (_, bearer) = signupAndLogin("img-del-404@example.com")

            val resp = deleteImage(UUID.randomUUID(), bearer)

            resp.statusCode shouldBe HttpStatus.NOT_FOUND
            codeOf(resp) shouldBe "listing_not_found"
        }

        test("an unauthenticated upload returns a generic 401") {
            val resp = putImage(UUID.randomUUID(), jpegBytes(), "image/jpeg", null)
            resp.statusCode shouldBe HttpStatus.UNAUTHORIZED
            codeOf(resp) shouldBe "unauthorized"
        }

        test("an unauthenticated remove returns a generic 401") {
            val resp = deleteImage(UUID.randomUUID(), null)
            resp.statusCode shouldBe HttpStatus.UNAUTHORIZED
            codeOf(resp) shouldBe "unauthorized"
        }

        test("an upload with no `image` part returns 400 invalid_input") {
            val (ownerId, bearer) = signupAndLogin("img-nopart@example.com")
            val listingId = createListing(bearer, ownerId)

            val headers = HttpHeaders().apply {
                contentType = MediaType.MULTIPART_FORM_DATA
                setBearerAuth(bearer)
            }
            val resp = client.exchange(
                url("/v1/listings/$listingId/image"),
                HttpMethod.PUT,
                HttpEntity<Any>(LinkedMultiValueMap<String, Any>(), headers),
                Map::class.java,
            )

            resp.statusCode shouldBe HttpStatus.BAD_REQUEST
            codeOf(resp) shouldBe "invalid_input"
        }

        test("an undecodable upload returns 400 invalid_input and stores nothing") {
            val (ownerId, bearer) = signupAndLogin("img-garbage@example.com")
            val listingId = createListing(bearer, ownerId)

            val resp = putImage(listingId, "this is not an image".toByteArray(), "image/jpeg", bearer)

            resp.statusCode shouldBe HttpStatus.BAD_REQUEST
            codeOf(resp) shouldBe "invalid_input"
            getImage(listingId, "full", bearer).statusCode shouldBe HttpStatus.NOT_FOUND
        }
    }

    /** POST /v1/listings and return the created listing id. */
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
            "isHandCut" to true,
        )
        val resp = client.exchange(url("/v1/listings"), HttpMethod.POST, HttpEntity<Any>(body, headers), Map::class.java)
        resp.statusCode shouldBe HttpStatus.CREATED
        resp.body!!["ownerId"].toString() shouldBe ownerId.toString()
        return UUID.fromString(resp.body!!["id"].toString())
    }

    /** PUT /v1/listings/{id}/image with a single `image` file part. */
    private fun putImage(
        listingId: UUID,
        bytes: ByteArray,
        contentType: String,
        bearer: String?,
    ): ResponseEntity<Map<*, *>> {
        val headers = HttpHeaders()
        headers.contentType = MediaType.MULTIPART_FORM_DATA
        if (bearer != null) headers.setBearerAuth(bearer)
        val body = LinkedMultiValueMap<String, Any>()
        body.add("image", UploadResource(bytes, "hero.jpg"))
        return client.exchange(
            url("/v1/listings/$listingId/image"),
            HttpMethod.PUT,
            HttpEntity<Any>(body, headers),
            Map::class.java,
        )
    }

    private fun deleteImage(listingId: UUID, bearer: String?): ResponseEntity<Map<*, *>> {
        val headers = HttpHeaders()
        if (bearer != null) headers.setBearerAuth(bearer)
        return client.exchange(
            url("/v1/listings/$listingId/image"),
            HttpMethod.DELETE,
            HttpEntity<Any>(null, headers),
            Map::class.java,
        )
    }

    /** GET /v1/listings/{id}/image?variant=... — the sc-157 read endpoint. */
    private fun getImage(listingId: UUID, variant: String, bearer: String?): ResponseEntity<ByteArray> {
        val headers = HttpHeaders()
        if (bearer != null) headers.setBearerAuth(bearer)
        return client.exchange(
            url("/v1/listings/$listingId/image?variant=$variant"),
            HttpMethod.GET,
            HttpEntity<Any>(null, headers),
            ByteArray::class.java,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun codeOf(resp: ResponseEntity<Map<*, *>>): String? =
        resp.body?.let { it["code"] as? String }

    /** A valid, decodable JPEG (a solid-color 3000x2000 image). */
    private fun jpegBytes(width: Int = 3000, height: Int = 2000): ByteArray {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = Color(40, 90, 180)
        g.fillRect(0, 0, width, height)
        g.dispose()
        val out = ByteArrayOutputStream()
        ImageIO.write(img, "jpeg", out)
        return out.toByteArray()
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

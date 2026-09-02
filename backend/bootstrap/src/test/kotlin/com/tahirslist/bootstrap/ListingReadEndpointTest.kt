package com.tahirslist.bootstrap

import com.tahirslist.application.image.ImagePort
import com.tahirslist.application.image.ImageVariant
import com.tahirslist.application.image.InMemoryImagePort
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.ApplicationContext
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
import org.springframework.web.client.ResponseErrorHandler
import org.springframework.web.client.RestTemplate
import java.util.UUID

/**
 * sc-157 public read surface (docs/design/sc-157-image-variants.md §"URL / serving
 * contract"): end-to-end against the full application graph with the real Flyway
 * seed data and the in-memory [ImagePort] fallback (no MinIO — the S3 adapter is
 * gated behind `app.storage.s3.endpoint`, which boot tests never set).
 *
 *  - browse carries thumbnail-size URLs ONLY — full-res never appears on a card;
 *  - detail carries the full-res `imageUrl` (and MAY carry the thumbnail);
 *  - the image proxy returns the exact requested variant bytes with the stored
 *    content-type + `Cache-Control: public, max-age=86400`;
 *  - unknown variant = 400, absent image = 404;
 *  - the list and sub-resource routes under /v1/listings are public (search /
 *    browse is core public UX) while writes stay authenticated (deny-by-default);
 *  - the context boots without MinIO, the InMemoryImagePort fallback bean wins,
 *    and the seed-image ingest runner stays gated off so boot tests never hit
 *    the network.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ListingReadEndpointTest : PostgresBootTest() {

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var images: ImagePort

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var context: ApplicationContext

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

    private fun seededListingId(name: String): UUID =
        jdbc.queryForObject(
            "SELECT id FROM restaurant_listings WHERE name = ? LIMIT 1",
            UUID::class.java,
            name,
        ) ?: error("expected a seed row named '$name'")

    private fun getBytes(path: String): ResponseEntity<ByteArray> =
        client.exchange(url(path), HttpMethod.GET, HttpEntity.EMPTY, ByteArray::class.java)

    init {
        test("browse cards expose a thumbnail URL ONLY — full-res never appears on cards") {
            val body = restTemplate.getForEntity("/v1/listings", String::class.java).body!!

            val cards: JsonNode = objectMapper.readTree(body)
            cards.isArray shouldBe true
            (cards.size() >= 5) shouldBe true // the 30-seed DB backs the browse surface

            cards.forEach { card ->
                card.has("imageThumbnailUrl") shouldBe true
                val thumbUrl = card.get("imageThumbnailUrl").asText()
                thumbUrl.contains("/v1/listings/") shouldBe true
                thumbUrl.contains("variant=thumbnail") shouldBe true
                // The sc-157 "no oversized fetch on cards" rule.
                card.has("imageUrl") shouldBe false
                thumbUrl.contains("variant=full") shouldBe false
            }
        }

        test("detail carries the full-res imageUrl and the thumbnail URL") {
            val id = seededListingId("Aroma Fine Indian Cuisine")

            val body = restTemplate.getForEntity("/v1/listings/$id", String::class.java).body!!

            val detail: JsonNode = objectMapper.readTree(body)
            detail.get("imageUrl").asText().contains("variant=full") shouldBe true
            detail.get("imageThumbnailUrl").asText().contains("variant=thumbnail") shouldBe true
            detail.get("imageThumbnailUrl").asText().contains("variant=full") shouldBe false

            // Detail hero also gets the responsive sc-183 width set.
            detail.has("imageSrcset") shouldBe true
            detail.get("imageSrcset").size() shouldBe ImageVariant.thumbnailVariants.size
        }

        test("browse and detail cards expose the structured address fields (sc-187)") {
            // Browse card for a US seed row surfaces city/province/postal/country.
            val browseBody = restTemplate.getForEntity("/v1/listings", String::class.java).body!!
            val cards: JsonNode = objectMapper.readTree(browseBody)
            val alAmir = (0 until cards.size()).map { cards[it] }
                .first { it.get("name").asText() == "Al-Amir Lebanese Restaurant & Club" }
            alAmir.get("address").asText() shouldBe "3885 Belt Line Rd"  // street line preserved
            alAmir.get("city").asText() shouldBe "Addison"
            alAmir.get("province").asText() shouldBe "TX"
            alAmir.get("postal").asText() shouldBe "75001"
            alAmir.get("country").asText() shouldBe "US"

            // Detail for a CA seed row surfaces the same fields (non-US locality).
            val id = seededListingId("Aroma Fine Indian Cuisine")
            val detailBody = restTemplate.getForEntity("/v1/listings/$id", String::class.java).body!!
            val detail: JsonNode = objectMapper.readTree(detailBody)
            detail.get("address").asText() shouldBe "287 King St W"
            detail.get("city").asText() shouldBe "Toronto"
            detail.get("province").asText() shouldBe "ON"
            detail.get("postal").asText() shouldBe "M5V 0W3"
            detail.get("country").asText() shouldBe "CA"
        }

        test("detail for an unknown listing returns 404") {
            val resp = getBytes("/v1/listings/${UUID.randomUUID()}")

            resp.statusCode shouldBe HttpStatus.NOT_FOUND
        }

        test("the image proxy serves the exact requested variant bytes with content-type + cache header") {
            val id = UUID.randomUUID()
            val thumbBytes = "thumb-bytes-0001".toByteArray()
            val fullBytes = "full-bytes-9001".toByteArray()
            images.save(id, ImageVariant.THUMBNAIL_400, "image/jpeg", thumbBytes)
            images.save(id, ImageVariant.FULL, "image/png", fullBytes)

            val thumb = getBytes("/v1/listings/$id/image?variant=thumbnail")
            thumb.statusCode shouldBe HttpStatus.OK
            thumb.headers.contentType shouldBe MediaType.parseMediaType("image/jpeg")
            assertPublicCached(thumb)
            thumb.body!!.contentEquals(thumbBytes) shouldBe true

            val full = getBytes("/v1/listings/$id/image?variant=full")
            full.statusCode shouldBe HttpStatus.OK
            full.headers.contentType shouldBe MediaType.parseMediaType("image/png")
            assertPublicCached(full)
            full.body!!.contentEquals(fullBytes) shouldBe true
        }

        test("the image proxy serves each sc-183 thumbnail width as a distinct variant") {
            val id = UUID.randomUUID()
            val slugByVariant = mapOf(
                ImageVariant.THUMBNAIL_400 to "thumbnail",
                ImageVariant.THUMBNAIL_768 to "thumbnail_768",
                ImageVariant.THUMBNAIL_1280 to "thumbnail_1280",
                ImageVariant.THUMBNAIL_1920 to "thumbnail_1920",
            )
            slugByVariant.forEach { (variant, _) ->
                images.save(id, variant, "image/jpeg", "${variant.name}-bytes".toByteArray())
            }

            slugByVariant.forEach { (variant, slug) ->
                val resp = getBytes("/v1/listings/$id/image?variant=$slug")
                resp.statusCode shouldBe HttpStatus.OK
                resp.headers.contentType shouldBe MediaType.parseMediaType("image/jpeg")
                resp.body!!.contentEquals("${variant.name}-bytes".toByteArray()) shouldBe true
            }
        }

        test("browse cards carry an imageSrcset covering every sc-183 width, never the full-res variant") {
            val body = restTemplate.getForEntity("/v1/listings", String::class.java).body!!

            val cards: JsonNode = objectMapper.readTree(body)
            cards.forEach { card ->
                card.has("imageSrcset") shouldBe true
                val srcset = card.get("imageSrcset")
                srcset.isArray shouldBe true
                srcset.size() shouldBe ImageVariant.thumbnailVariants.size
                val widths = srcset.map { it.get("width").asInt() }
                widths shouldBe listOf(400, 768, 1280, 1920)
                srcset.forEach { entry ->
                    val url = entry.get("url").asText()
                    url.contains("/v1/listings/") shouldBe true
                    // srcset is a thumbnail *set* — full-res never leaks into it.
                    url.contains("variant=full") shouldBe false
                }
            }
        }

        test("the image proxy returns 400 for an unknown variant") {
            val id = UUID.randomUUID()
            images.save(id, ImageVariant.THUMBNAIL_400, "image/jpeg", "x".toByteArray())

            val resp = getBytes("/v1/listings/$id/image?variant=blur")

            resp.statusCode shouldBe HttpStatus.BAD_REQUEST
        }

        test("the image proxy returns 404 when no image is stored for the variant") {
            val resp = getBytes("/v1/listings/${UUID.randomUUID()}/image?variant=thumbnail")

            resp.statusCode shouldBe HttpStatus.NOT_FOUND
        }

        test("public browse/detail reads need no token; writes stay authenticated (401)") {
            // Public reads (already exercised above without a token) work; a write
            // without a token is refused by the deny-by-default resource server.
            val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
            val resp = client.exchange(
                url("/v1/listings"),
                HttpMethod.POST,
                HttpEntity(mapOf("name" to "X", "address" to "1 St", "lat" to 1.0, "lng" to 1.0), headers),
                Map::class.java,
            )

            resp.statusCode shouldBe HttpStatus.UNAUTHORIZED
            resp.body!!["code"] shouldBe "unauthorized"
        }

        test("the context boots without MinIO and the InMemoryImagePort fallback wins") {
            // `app.storage.s3.endpoint` is absent in tests, so the S3 adapter is
            // never activated and the @ConditionalOnMissingBean fallback is the
            // single ImagePort bean. A real deployment configures S3 instead.
            images::class shouldBe InMemoryImagePort::class
        }

        test("the seed-image ingest runner is gated OFF by default (boot tests never hit the network)") {
            val runners = context.getBeanNamesForType(ApplicationRunner::class.java)
            ("seedImageIngestRunner" in runners) shouldBe false

            // The manifest is on the classpath for the gated runner's default
            // `classpath:seed-photos.json`, ready for an explicitly-enabled ingest.
            ClassPathResource("seed-photos.json").exists() shouldBe true
        }
    }

    private fun assertPublicCached(resp: ResponseEntity<ByteArray>) {
        // Spring may serialize the Cache-Control directive in either order; the
        // contract is that it is public and cached for one day, not the ordering.
        val cc = resp.headers.getFirst("Cache-Control") ?: ""
        cc.split(",").map { it.trim() } shouldContainAll listOf("max-age=86400", "public")
    }
}
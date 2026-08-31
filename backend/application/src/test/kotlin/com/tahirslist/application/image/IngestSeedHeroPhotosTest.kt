package com.tahirslist.application.image

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.imageio.ImageIO

/**
 * IngestSeedHeroPhotos orchestrates resolver → fetcher → IngestHeroImage. These
 * tests assert the isolation contract: a good row lands while an unresolved or
 * failing row is reported, never aborting the batch.
 */
class IngestSeedHeroPhotosTest : FunSpec() {

    private fun jpeg(width: Int, height: Int): ByteArray {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = Color(120, 60, 220)
        g.fillRect(0, 0, width, height)
        g.dispose()
        val out = ByteArrayOutputStream()
        ImageIO.write(img, "jpeg", out)
        return out.toByteArray()
    }

    private val okImage: ByteArray = jpeg(1000, 700)

    init {
        test("resolved + fetched + decodable photo is ingested as FULL + THUMBNAIL") {
            val port = InMemoryImagePort()
            val id = UUID.randomUUID()

            val useCase = IngestSeedHeroPhotos(
                resolver = { photo ->
                    SeedPhotoResolution.Resolved(if (photo.name == "Lazeez") id else UUID.randomUUID())
                },
                fetcher = { okImage },
                ingestHeroImage = IngestHeroImage(port),
            )

            val results = useCase.ingestAll(listOf(SeedHeroPhoto("Lazeez", "Toronto", "Rogers Rd", "http://img/1")))

            results.single().status shouldBe SeedPhotoIngestResult.Status.INGESTED
            port.load(id, ImageVariant.FULL).shouldNotBeNull()
            port.load(id, ImageVariant.THUMBNAIL).shouldNotBeNull()
        }

        test("unresolved photo is reported WITHOUT touching the store") {
            val port = InMemoryImagePort()
            val useCase = IngestSeedHeroPhotos(
                resolver = { SeedPhotoResolution.Unresolved("no matching seed listing") },
                fetcher = { okImage },
                ingestHeroImage = IngestHeroImage(port),
            )

            val results = useCase.ingestAll(listOf(SeedHeroPhoto("Ghost", null, null, "http://img/x")))

            results.single().status shouldBe SeedPhotoIngestResult.Status.UNRESOLVED
            results.single().listingId shouldBe null
            // Store never touched.
            results.single().photoName shouldBe "Ghost"
        }

        test("fetch failure is isolated and reported as FETCH_FAILED") {
            val port = InMemoryImagePort()
            val id = UUID.randomUUID()
            val useCase = IngestSeedHeroPhotos(
                resolver = { SeedPhotoResolution.Resolved(id) },
                fetcher = { throw ImageFetchException("unreachable") },
                ingestHeroImage = IngestHeroImage(port),
            )

            val results = useCase.ingestAll(listOf(SeedHeroPhoto("A", "NYC", "St", "http://img/x")))

            results.single().status shouldBe SeedPhotoIngestResult.Status.FETCH_FAILED
            port.load(id, ImageVariant.FULL) shouldBe null
        }

        test("undecodable fetched bytes are reported as STORE_FAILED (bad image)") {
            val port = InMemoryImagePort()
            val id = UUID.randomUUID()
            val useCase = IngestSeedHeroPhotos(
                resolver = { SeedPhotoResolution.Resolved(id) },
                fetcher = { "not-an-image".toByteArray() },
                ingestHeroImage = IngestHeroImage(port),
            )

            val results = useCase.ingestAll(listOf(SeedHeroPhoto("B", "Austin", "Main", "http://img/y")))

            results.single().status shouldBe SeedPhotoIngestResult.Status.STORE_FAILED
            port.load(id, ImageVariant.FULL) shouldBe null
        }

        test("one failing row does not abort the others (batch isolation)") {
            val port = InMemoryImagePort()
            val goodId = UUID.randomUUID()

            val useCase = IngestSeedHeroPhotos(
                resolver = { SeedPhotoResolution.Resolved(goodId) },
                fetcher = { if (it.endsWith("/ok")) okImage else throw ImageFetchException("down") },
                ingestHeroImage = IngestHeroImage(port),
            )

            val results = useCase.ingestAll(
                listOf(
                    SeedHeroPhoto("Good", null, null, "http://img/ok"),
                    SeedHeroPhoto("Bad", null, null, "http://img/nope"),
                ),
            )

            results.map { it.status } shouldContainExactlyInAnyOrder listOf(
                SeedPhotoIngestResult.Status.INGESTED, // Good: fetched + stored
                SeedPhotoIngestResult.Status.FETCH_FAILED, // Bad: fetch throws
            )
            // The good row still landed despite the bad row failing.
            port.load(goodId, ImageVariant.FULL).shouldNotBeNull()
        }
    }
}
package com.tahirslist.application.image

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.imageio.ImageIO

/**
 * IngestHeroImage is the piece that turns an original into FULL + one thumbnail
 * per sc-183 width (400/768/1280/1920). These tests pin the write ordering
 * (resize validated FIRST so a bad image can never leave a half-written original)
 * through an in-memory port.
 */
class IngestHeroImageTest : FunSpec() {

    private fun jpeg(width: Int, height: Int): ByteArray {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = Color(40, 90, 180)
        g.fillRect(0, 0, width, height)
        g.dispose()
        val out = ByteArrayOutputStream()
        ImageIO.write(img, "jpeg", out)
        return out.toByteArray()
    }

    init {
        test("stores FULL original and every sc-183 thumbnail width as a distinct variant") {
            val port = InMemoryImagePort()
            val useCase = IngestHeroImage(port)
            val id = UUID.randomUUID()
            val original = jpeg(3000, 2000)

            useCase.ingest(id, StoredImage(original, "image/jpeg"))

            val full = port.load(id, ImageVariant.FULL)!!
            full.contentType shouldBe "image/jpeg"
            full.bytes.contentEquals(original) shouldBe true

            // Every thumbnail width is stored, at exactly its target width.
            ImageVariant.thumbnailVariants.forEach { variant ->
                val thumb = port.load(id, variant).shouldNotBeNull()
                val decoded = ImageIO.read(java.io.ByteArrayInputStream(thumb.bytes))
                decoded.width shouldBe variant.widthPx
            }
            // 400 => height 266 for a 3:2 original.
            val small = port.load(id, ImageVariant.THUMBNAIL_400)!!
            val smallDecoded = ImageIO.read(java.io.ByteArrayInputStream(small.bytes))
            smallDecoded.width shouldBe 400
            smallDecoded.height shouldBe (400 * 2000 / 3000) // 266
        }

        test("unreadable input throws before anything is persisted") {
            val port = InMemoryImagePort()
            val useCase = IngestHeroImage(port)
            val id = UUID.randomUUID()

            shouldThrow<IllegalArgumentException> {
                useCase.ingest(id, StoredImage("garbage".toByteArray(), "image/jpeg"))
            }

            // No variant may exist — no half-ingested row.
            port.load(id, ImageVariant.FULL) shouldBe null
            ImageVariant.thumbnailVariants.forEach { port.load(id, it) shouldBe null }
        }
    }
}
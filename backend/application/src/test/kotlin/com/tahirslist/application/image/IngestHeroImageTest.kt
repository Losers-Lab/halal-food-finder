package com.tahirslist.application.image

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.imageio.ImageIO

/**
 * IngestHeroImage is the piece that turns an original into FULL + THUMBNAIL
 * variants. These tests pin the write ordering (resize validated FIRST so a bad
 * image can never leave a half-written original) through an in-memory port.
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
        test("stores FULL original and a ≤400px THUMBNAIL variant") {
            val port = InMemoryImagePort()
            val useCase = IngestHeroImage(port)
            val id = UUID.randomUUID()
            val original = jpeg(1500, 1000)

            useCase.ingest(id, StoredImage(original, "image/jpeg"))

            val full = port.load(id, ImageVariant.FULL)!!
            full.contentType shouldBe "image/jpeg"
            full.bytes.contentEquals(original) shouldBe true

            val thumb = port.load(id, ImageVariant.THUMBNAIL)!!
            val decoded = ImageIO.read(java.io.ByteArrayInputStream(thumb.bytes))
            decoded.width shouldBe 400
            decoded.height shouldBe (400.0 * 1000.0 / 1500.0).toInt() // 266
        }

        test("unreadable input throws before anything is persisted") {
            val port = InMemoryImagePort()
            val useCase = IngestHeroImage(port)
            val id = UUID.randomUUID()

            shouldThrow<IllegalArgumentException> {
                useCase.ingest(id, StoredImage("garbage".toByteArray(), "image/jpeg"))
            }

            // Neither variant may exist — no half-ingested row.
            port.load(id, ImageVariant.FULL) shouldBe null
            port.load(id, ImageVariant.THUMBNAIL) shouldBe null
        }
    }
}
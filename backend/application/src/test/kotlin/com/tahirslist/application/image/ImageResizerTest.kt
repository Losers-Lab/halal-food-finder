package com.tahirslist.application.image

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * ImageResizer is pure JDK ImageIO: no container, no network. Unit tests cover
 * the sizing policy that underpins the sc-157/sc-183 variant contracts:
 *  - each thumbnail never exceeds its target width,
 *  - never upscale (small originals are returned unchanged),
 *  - aspect ratio preserved,
 *  - unreadable input rejected loudly before a half-ingested row can occur.
 *
 * sc-183: multiple widths (400/768/1280/1920) are produced via [ImageResizer.resizeToWidth],
 * sized to the monitor's max resolution (~full-HD 1920) rather than any live
 * container/viewport width (founder binding spec).
 */
class ImageResizerTest : FunSpec() {

    /** Build a width×height opaque jpeg. */
    private fun jpeg(width: Int, height: Int): ByteArray {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = Color(200, 100, 60)
        g.fillRect(0, 0, width, height)
        g.dispose()
        val out = ByteArrayOutputStream()
        ImageIO.write(img, "jpeg", out)
        return out.toByteArray()
    }

    private fun decodedWidth(bytes: ByteArray): Int =
        ImageIO.read(java.io.ByteArrayInputStream(bytes)).width

    private val contentType = "image/jpeg"

    init {
        test("large jpeg is downscaled to each target width, aspect preserved") {
            val original = StoredImage(jpeg(3000, 2000), contentType)

            val w400 = ImageResizer.resizeToWidth(original, 400)
            decodedWidth(w400.bytes) shouldBe 400
            ImageIO.read(java.io.ByteArrayInputStream(w400.bytes)).height shouldBe (400 * 2000 / 3000)

            val w768 = ImageResizer.resizeToWidth(original, 768)
            decodedWidth(w768.bytes) shouldBe 768
            ImageIO.read(java.io.ByteArrayInputStream(w768.bytes)).height shouldBe (768 * 2000 / 3000)

            val w1280 = ImageResizer.resizeToWidth(original, 1280)
            decodedWidth(w1280.bytes) shouldBe 1280
            ImageIO.read(java.io.ByteArrayInputStream(w1280.bytes)).height shouldBe (1280 * 2000 / 3000)

            val w1920 = ImageResizer.resizeToWidth(original, 1920)
            decodedWidth(w1920.bytes) shouldBe 1920
            ImageIO.read(java.io.ByteArrayInputStream(w1920.bytes)).height shouldBe (1920 * 2000 / 3000)

            w400.contentType shouldBe "image/jpeg"
            w768.contentType shouldBe "image/jpeg"
            w1280.contentType shouldBe "image/jpeg"
            w1920.contentType shouldBe "image/jpeg"
        }

        test("thumbnail never exceeds the target width for any input") {
            val original = StoredImage(jpeg(3000, 2000), contentType)
            decodedWidth(ImageResizer.resizeToWidth(original, 400).bytes) shouldBe 400
            decodedWidth(ImageResizer.resizeToWidth(original, 768).bytes) shouldBe 768
            decodedWidth(ImageResizer.resizeToWidth(original, 1280).bytes) shouldBe 1280
            decodedWidth(ImageResizer.resizeToWidth(original, 1920).bytes) shouldBe 1920
        }

        test("smaller-than-target originals are returned unchanged (never upscale)") {
            val original = StoredImage(jpeg(200, 150), contentType)

            ImageResizer.resizeToWidth(original, 1920).bytes.contentEquals(original.bytes) shouldBe true
            val decoded = ImageIO.read(java.io.ByteArrayInputStream(original.bytes))
            decoded.width shouldBe 200
            decoded.height shouldBe 150
        }

        test("PNG with alpha is re-encoded as PNG (transparency preserved)") {
            val img = BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB)
            val out = ByteArrayOutputStream()
            ImageIO.write(img, "png", out)

            val thumb = ImageResizer.resizeToWidth(StoredImage(out.toByteArray(), "image/png"), 400)
            thumb.contentType shouldBe "image/png"
            val decoded = ImageIO.read(java.io.ByteArrayInputStream(thumb.bytes))
            decoded.width shouldBe 400
            decoded.colorModel.hasAlpha() shouldBe true
        }

        test("unreadable bytes are rejected with IllegalArgumentException") {
            shouldThrow<IllegalArgumentException> {
                ImageResizer.resizeToWidth(StoredImage("not-an-image".toByteArray(), "image/jpeg"), 400)
            }
        }
    }
}
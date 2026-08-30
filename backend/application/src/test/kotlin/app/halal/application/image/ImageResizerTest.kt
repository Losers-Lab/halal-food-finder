package app.halal.application.image

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * ImageResizer is pure JDK ImageIO: no container, no network. Unit tests cover
 * the sizing policy that underpins the sc-157 variant contract:
 *  - thumbnails never exceed the target width,
 *  - never upscale (small originals are returned unchanged),
 *  - aspect ratio preserved,
 *  - unreadable input rejected loudly before a half-ingested row can occur.
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

    private val contentType = "image/jpeg"

    init {
        test("large jpeg is downscaled to the target width, aspect preserved") {
            val original = StoredImage(jpeg(1200, 800), contentType)
            val thumb = ImageResizer.resizeToThumb(original)

            // Re-decode to assert dimensions.
            val decoded = ImageIO.read(java.io.ByteArrayInputStream(thumb.bytes))
            decoded.width shouldBe 400
            decoded.height shouldBe (400.0 * 800.0 / 1200.0).toInt() // 266
            decoded.height shouldBe 266
            thumb.contentType shouldBe "image/jpeg"
        }

        test("thumbnail never exceeds the target width for any input") {
            val original = StoredImage(jpeg(3000, 2000), contentType)
            val decoded = ImageIO.read(java.io.ByteArrayInputStream(ImageResizer.resizeToThumb(original).bytes))
            decoded.width shouldBe 400
        }

        test("smaller-than-target originals are returned unchanged (never upscale)") {
            val original = StoredImage(jpeg(200, 150), contentType)
            val thumb = ImageResizer.resizeToThumb(original)

            val decoded = ImageIO.read(java.io.ByteArrayInputStream(thumb.bytes))
            decoded.width shouldBe 200
            decoded.height shouldBe 150
        }

        test("PNG with alpha is re-encoded as PNG (transparency preserved)") {
            val img = BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB)
            val out = ByteArrayOutputStream()
            ImageIO.write(img, "png", out)

            val thumb = ImageResizer.resizeToThumb(StoredImage(out.toByteArray(), "image/png"))
            thumb.contentType shouldBe "image/png"
            val decoded = ImageIO.read(java.io.ByteArrayInputStream(thumb.bytes))
            decoded.width shouldBe 400
            decoded.colorModel.hasAlpha() shouldBe true
        }

        test("unreadable bytes are rejected with IllegalArgumentException") {
            shouldThrow<IllegalArgumentException> {
                ImageResizer.resizeToThumb(StoredImage("not-an-image".toByteArray(), "image/jpeg"))
            }
        }
    }
}
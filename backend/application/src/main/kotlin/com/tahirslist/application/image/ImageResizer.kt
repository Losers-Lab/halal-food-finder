package com.tahirslist.application.image

import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * Pure JDK image resize for thumbnail pre-generation (docs/design/sc-157-image-variants.md).
 *
 * Uses `ImageIO` + a high-quality `Graphics2D` downscale — no third-party
 * imaging dependency, so the thumbnail step stays in the framework-free
 * application layer (and in the classpath of every adapter).
 *
 * Policy:
 *  - Output is re-encoded to `image/jpeg` (quality 0.85) for photographic
 *    source; PNG with alpha is kept as PNG so transparency is preserved.
 *  - **Never upscales**: the thumbnail width is `min(targetWidth, originalWidth)`.
 *  - Aspect ratio is preserved; only width is the target.
 *  - [IllegalArgumentException] on unreadable / unsupported input (empty bytes,
 *    not a decodable image) — a symptom of bad data, surfaced loudly not silently.
 */
object ImageResizer {

    private const val JPEG_QUALITY = 0.85f

    /**
     * Produce a thumbnail no wider than [targetWidth] (default: the
     * [ImageVariant.THUMBNAIL] width), preserving aspect ratio.
     *
     * @return the resized image as [StoredImage] with a concrete contentType
     *         (`image/jpeg` or `image/png`).
     * @throws IllegalArgumentException if [original.bytes] is not a decodable image.
     */
    fun resizeToThumb(
        original: StoredImage,
        targetWidth: Int = ImageVariant.THUMBNAIL.widthPx ?: 400,
    ): StoredImage {
        if (targetWidth <= 0) throw IllegalArgumentException("targetWidth must be positive")

        val buffered = ImageIO.read(ByteArrayInputStream(original.bytes))
            ?: throw IllegalArgumentException("Input is not a decodable image (contentType=${original.contentType})")

        val originalWidth = buffered.width
        val scale = if (originalWidth <= targetWidth) 1.0 else targetWidth.toDouble() / originalWidth
        if (scale >= 1.0) return original // no upscale; already small enough

        val hasAlpha = buffered.colorModel.hasAlpha()
        val targetHeight = (buffered.height * scale).toInt().coerceAtLeast(1)
        val bufferType = if (hasAlpha) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB
        val resized = BufferedImage(targetWidth, targetHeight, bufferType)
        val g2 = resized.createGraphics()
        try {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g2.drawImage(buffered, 0, 0, targetWidth, targetHeight, null)
        } finally {
            g2.dispose()
        }

        val contentType = if (hasAlpha) "image/png" else "image/jpeg"
        val out = ByteArrayOutputStream()
        val written = if (hasAlpha) {
            ImageIO.write(resized, "png", out)
        } else {
            val jpg = ImageIO.createImageOutputStream(out)
            try {
                ImageIO.write(resized, "jpeg", jpg)
            } finally {
                jpg.close()
            }
        }
        if (!written) throw IllegalArgumentException("Could not re-encode resized image")
        return StoredImage(bytes = out.toByteArray(), contentType = contentType)
    }
}
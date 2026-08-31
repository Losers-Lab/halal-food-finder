package app.halal.application.image

/**
 * The stored bytes of one image [ImageVariant], with its media type so the
 * serving adapter can set the correct `Content-Type` without re-sniffing bytes.
 */
data class StoredImage(
    val bytes: ByteArray,
    val contentType: String,
)

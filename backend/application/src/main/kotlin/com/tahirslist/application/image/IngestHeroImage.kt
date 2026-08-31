package com.tahirslist.application.image

import java.util.UUID

/**
 * Ingest (or re-ingest) one restaurant's hero image: stores the full-res
 * original and a pre-generated thumbnail variant (docs/design/sc-157-image-variants.md).
 *
 * Thumbnail pre-generation happens HERE (at ingest), not on the serving path, so
 * a card never receives oversized bytes or triggers on-the-fly processing. Both
 * variants are written independently to [ImagePort].
 *
 * @throws IllegalArgumentException if [original] is not a decodable image (the
 *         resize step rejects it before anything is persisted).
 */
class IngestHeroImage(private val port: ImagePort) {

    /**
     * Store [original] as the listing's hero: FULL = original bytes, THUMBNAIL =
     * a ≤400px-wide downscale.
     *
     * @param listingId the owning restaurant listing.
     * @param original the original image bytes + its content type.
     */
    fun ingest(listingId: UUID, original: StoredImage) {
        // Resize first: reject a bad image before persisting the original, so a
        // half-ingested row (original saved, thumb failed) is impossible.
        val thumbnail = ImageResizer.resizeToThumb(original)

        port.save(listingId, ImageVariant.FULL, original.contentType, original.bytes)
        port.save(listingId, ImageVariant.THUMBNAIL, thumbnail.contentType, thumbnail.bytes)
    }
}
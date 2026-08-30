package app.halal.application.image

import java.util.UUID

/**
 * Per-row ingest outcome, so a batch run reports which rows landed and why the
 * rest did not (docs/design/sc-157-image-variants.md — honest ingest, no silent
 * partial success).
 */
data class SeedPhotoIngestResult(
    val listingId: UUID?,
    val photoName: String,
    val status: Status,
    val message: String,
) {
    enum class Status { INGESTED, UNRESOLVED, FETCH_FAILED, STORE_FAILED }
}

/**
 * Orchestrates the seed hero-photo manifest ingest (docs/design/sc-157-image-variants.md).
 *
 * For each [SeedHeroPhoto]:
 *  1. resolve it to a seed listing id ([SeedPhotoListingResolver]);
 *  2. fetch the remote image bytes ([ImageFetcher]);
 *  3. store FULL + THUMBNAIL variants ([IngestHeroImage]).

 * Isolation: a failure on any one row (unresolved, fetch error, store error) is
 * reported in its [SeedPhotoIngestResult] and does NOT abort the batch — one bad
 * row cannot poison the rest. This mirrors how the seed listing migration
 * swallows row-level conflicts.
 */
class IngestSeedHeroPhotos(
    private val resolver: SeedPhotoListingResolver,
    private val fetcher: ImageFetcher,
    private val ingestHeroImage: IngestHeroImage,
) {

    fun ingestAll(photos: List<SeedHeroPhoto>): List<SeedPhotoIngestResult> =
        photos.map(this::ingestOne)

    private fun ingestOne(photo: SeedHeroPhoto): SeedPhotoIngestResult {
        val resolution = resolver.resolve(photo)
        val listingId = when (resolution) {
            is SeedPhotoResolution.Resolved -> resolution.listingId
            is SeedPhotoResolution.Unresolved ->
                return SeedPhotoIngestResult(
                    listingId = null,
                    photoName = photo.name,
                    status = SeedPhotoIngestResult.Status.UNRESOLVED,
                    message = resolution.reason,
                )
        }

        val bytes = try {
            fetcher.fetch(photo.heroUrl)
        } catch (e: ImageFetchException) {
            return SeedPhotoIngestResult(
                listingId = listingId,
                photoName = photo.name,
                status = SeedPhotoIngestResult.Status.FETCH_FAILED,
                message = "fetch ${photo.heroUrl}: ${e.message}",
            )
        }

        return try {
            val contentType = detectContentType(bytes)
            ingestHeroImage.ingest(listingId, StoredImage(bytes = bytes, contentType = contentType))
            SeedPhotoIngestResult(
                listingId = listingId,
                photoName = photo.name,
                status = SeedPhotoIngestResult.Status.INGESTED,
                message = "stored FULL + THUMBNAIL for ${photo.heroUrl}",
            )
        } catch (e: IllegalArgumentException) {
            SeedPhotoIngestResult(
                listingId = listingId,
                photoName = photo.name,
                status = SeedPhotoIngestResult.Status.STORE_FAILED,
                message = "store ${photo.heroUrl}: ${e.message}",
            )
        }
    }

    /**
     * A best-effort content-type guess for the fetched bytes. Production seed
     * sources are image/jpeg + image/png; fall back to octet-stream when we
     * cannot tell (ImageResizer still validates decodability before persisting).
     */
    private fun detectContentType(bytes: ByteArray): String {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
        return when {
            bytes.size >= 8 && bytes.copyOfRange(0, 4).contentEquals(png) -> "image/png"
            bytes.size >= 2 && bytes.copyOfRange(0, 2).contentEquals(jpeg) -> "image/jpeg"
            else -> "application/octet-stream"
        }
    }
}
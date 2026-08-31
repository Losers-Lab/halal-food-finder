package app.halal.application.image

/**
 * Failure fetching an image over the network via [ImageFetcher].
 *
 * Distinct from "no image at all": this is an external-service unavailability /
 * bad-response condition, surfaced so per-row ingest isolation can report it
 * rather than silently skipping (docs/design/sc-157-image-variants.md).
 */
class ImageFetchException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
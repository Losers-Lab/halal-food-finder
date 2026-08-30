package app.halal.application.image

/**
 * Which stored variant of a restaurant hero image to read or write.
 *
 * Variants are pre-generated at ingest time (see docs/design/sc-157-image-variants.md):
 * the original is stored as [FULL] and resized to ≤ [THUMBNAIL] pixels wide at
 * store time. Serving endpoints simply read the exact variant object, so a
 * browse/search card can be handed a small object without ever fetching the
 * full-res original ("no oversized fetch on cards").
 *
 * [widthPx] is the target max width for [THUMBNAIL]; `null` for [FULL] means
 * "keep the original dimensions". The value is a contract hint the resize step
 * uses; the port itself treats variants as atomic storage keys.
 */
enum class ImageVariant(val widthPx: Int?) {
    THUMBNAIL(400),
    FULL(null),
}
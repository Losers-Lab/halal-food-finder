package com.tahirslist.application.image

/**
 * Which stored variant of a restaurant hero image to read or write.
 *
 * Variants are pre-generated at ingest time (see docs/design/sc-157-image-variants.md):
 * the original is stored as [FULL] and resized to a set of fixed thumbnail widths
 * at store time. Serving endpoints simply read the exact variant object, so a
 * browse/search card can be handed a small object without ever fetching the
 * full-res original ("no oversized fetch on cards").
 *
 * sc-183 (mobile-blurry thumbnails): the thumbnail set covers the MONITOR'S MAX
 * RESOLUTION (~full-HD 1920 wide), not any live container / viewport / panel
 * width (founder binding spec) — so a card is sharp on a 1920 display no matter
 * the size it renders at. [thumbnailVariants] is the canonical ascending set a
 * frontend may turn into a responsive `srcset`.
 *
 * [widthPx] is the target max width for each thumbnail variant; `null` for [FULL]
 * means "keep the original dimensions". The value is a contract hint the resize
 * step uses; the port itself treats each variant as an atomic storage key.
 */
enum class ImageVariant(val widthPx: Int?) {
    THUMBNAIL_400(400),
    THUMBNAIL_768(768),
    THUMBNAIL_1280(1280),
    THUMBNAIL_1920(1920),
    FULL(null),
    ;

    companion object {
        /** The canonical thumbnail widths, ascending. Produced/served/surfaced as a set (sc-183). */
        val thumbnailVariants: List<ImageVariant> =
            listOf(THUMBNAIL_400, THUMBNAIL_768, THUMBNAIL_1280, THUMBNAIL_1920)
    }
}
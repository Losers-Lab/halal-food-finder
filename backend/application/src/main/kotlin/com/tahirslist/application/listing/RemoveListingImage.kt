package com.tahirslist.application.listing

import com.tahirslist.application.favorite.ListingNotFoundException
import com.tahirslist.application.image.ImagePort
import com.tahirslist.application.image.ImageVariant
import com.tahirslist.application.verification.NotListingOwnerException
import java.util.UUID

/**
 * Remove a listing's hero image (sc-54). An authenticated listing owner removes
 * their own listing's stored image — every variant (FULL + each sc-183
 * thumbnail width) is deleted via [ImagePort].
 *
 * Flow and its failure semantics ("what happens when it fails"):
 *
 *  1. The listing must already exist ([ListingNotFoundException] → 404).
 *  2. The acting account must be exactly the recorded `owner_id`
 *     ([NotListingOwnerException] → 403) — the same owner guard as
 *     [UpdateListing].
 *  3. Deletion is idempotent: any variant that was never stored is a silent
 *     no-op (the [ImagePort.delete] contract). Removing an image that the
 *     listing never had is therefore not an error — the read surface's
 *     "no image → placeholder" behaviour simply remains in effect.
 *
 * After removal, the listing's read surface renders its placeholder (an `<img>`
 * with no object to fetch; the frontend's RestaurantPhoto shows the kraft stamp
 * placeholder), which is exactly the "image removed" state the product wants.
 */
class RemoveListingImage(
    private val listings: RestaurantListingRepository,
    private val images: ImagePort,
) {

    /** Every variant a listing's single hero image occupies (FULL + sc-183 thumbs). */
    private val allVariants: List<ImageVariant> =
        listOf(ImageVariant.FULL) + ImageVariant.thumbnailVariants

    fun execute(listingId: UUID, ownerId: UUID) {
        val listing = listings.findById(listingId)
            ?: throw ListingNotFoundException(listingId)

        if (listing.ownerId != ownerId) {
            throw NotListingOwnerException(listingId, ownerId)
        }

        allVariants.forEach { images.delete(listingId, it) }
    }
}
package com.tahirslist.application.listing

import com.tahirslist.application.favorite.ListingNotFoundException
import com.tahirslist.application.image.ImagePort
import com.tahirslist.application.image.IngestHeroImage
import com.tahirslist.application.image.StoredImage
import com.tahirslist.application.verification.NotListingOwnerException
import java.util.UUID

/**
 * Add / replace a listing's hero image (sc-53). An authenticated listing owner
 * uploads an image for their own listing; the bytes are stored via the existing
 * [ImagePort] (S3/MinIO in production) as the FULL original plus every sc-183
 * thumbnail width.
 *
 * Flow and its failure semantics ("what happens when it fails"):
 *
 *  1. The listing must already exist ([ListingNotFoundException] → 404).
 *  2. The acting account must be exactly the recorded `owner_id`
 *     ([NotListingOwnerException] → 403) — the same owner guard as
 *     [UpdateListing]. RBAC is enforced at the edge (deny-by-default resource
 *     server, JWT `sub`); this is the second, owner-scoped guard.
 *  3. The bytes must be a decodable image; [IngestHeroImage] resizes and stores
 *     every thumbnail width FIRST and rejects an undecodable upload with an
 *     [IllegalArgumentException] (→ 400) before anything is persisted — a
 *     half-written row is impossible.
 *
 * This is a **last-write-wins replace**: the listing has exactly ONE hero
 * image (the sc-157 read surface exposes a single hero), so uploading again
 * simply overwrites the previously stored variants. Gallery/multi-image
 * management is explicitly deferred (docs/design/sc-157-image-variants.md
 * "Open / unresolved").
 */
class AddListingImage(
    private val listings: RestaurantListingRepository,
    private val images: ImagePort,
) {

    private val ingest: IngestHeroImage = IngestHeroImage(images)

    fun execute(listingId: UUID, ownerId: UUID, original: StoredImage) {
        val listing = listings.findById(listingId)
            ?: throw ListingNotFoundException(listingId)

        if (listing.ownerId != ownerId) {
            throw NotListingOwnerException(listingId, ownerId)
        }

        ingest.ingest(listingId, original)
    }
}
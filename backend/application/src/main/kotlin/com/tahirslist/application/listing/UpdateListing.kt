package com.tahirslist.application.listing

import com.tahirslist.application.favorite.ListingNotFoundException
import com.tahirslist.application.verification.NotListingOwnerException
import com.tahirslist.domain.restaurant.CrossContamination
import com.tahirslist.domain.restaurant.Cuisine
import com.tahirslist.domain.restaurant.HalalItem
import com.tahirslist.domain.restaurant.HalalScope
import com.tahirslist.domain.restaurant.LatLng
import com.tahirslist.domain.restaurant.RestaurantListing
import java.util.UUID

/**
 * Update Listing use case (sc-23/47/48): an authenticated listing **owner** edits
 * their own listing's editable content fields.
 *
 * Flow and its failure semantics ("what happens when it fails"):
 *
 *  1. The listing must already exist ([ListingNotFoundException] → 404).
 *  2. The acting account must be exactly the recorded `owner_id`
 *     ([NotListingOwnerException] → 403). RBAC is enforced at the edge
 *     (deny-by-default resource server, JWT `sub`); this is the second,
 *     owner-scoped guard — the acting account is always the JWT sub, never a
 *     client-supplied owner.
 *  3. The editable fields are validated/trimmed by the domain via
 *     [RestaurantListing.withUpdatedFields] — identity + governance fields
 *     (id, ownerId, verificationStatus, createdAt, price, rating, brandId,
 *     provenance) are preserved untouched. A listing edit cannot un-own, un-link
 *     or re-verify a listing; those run through the claim/verification vertical.
 *  4. The replaced aggregate is persisted atomically (source + search mirror +
 *     multi-cuisine/halal-item child stores) and returned.
 *
 * This is a full replace of the *editable content fields* (mirrors the
 * Add Listing request shape, reusing the same frontend form) — not a sparse
 * field-level patch. An omitted nullable boolean (isHandCut/isDelivery) clears
 * it to null (unknown), consistent with the create tri-state semantics.
 */
class UpdateListing(
    private val listings: RestaurantListingRepository,
) {

    fun execute(
        listingId: UUID,
        ownerId: UUID,
        name: String,
        address: String,
        location: LatLng,
        cuisine: Cuisine,
        isHandCut: Boolean?,
        isDelivery: Boolean?,
        halalScope: HalalScope = HalalScope.DEFAULT,
        halalItems: Set<HalalItem> = emptySet(),
        crossContamination: CrossContamination = CrossContamination.DEFAULT,
        alcoholServed: Boolean = false,
    ): RestaurantListing {
        val current = listings.findById(listingId)
            ?: throw ListingNotFoundException(listingId)

        if (current.ownerId != ownerId) {
            throw NotListingOwnerException(listingId, ownerId)
        }

        // Domain validates/trims local input and preserves governance fields.
        val updated = current.withUpdatedFields(
            name = name,
            address = address,
            location = location,
            cuisine = cuisine,
            isHandCut = isHandCut,
            isDelivery = isDelivery,
            halalScope = halalScope,
            halalItems = halalItems,
            crossContamination = crossContamination,
            alcoholServed = alcoholServed,
        )

        return listings.update(updated)
            ?: throw ListingNotFoundException(listingId)
    }
}
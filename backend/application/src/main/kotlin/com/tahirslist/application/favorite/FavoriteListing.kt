package com.tahirslist.application.favorite

import com.tahirslist.application.listing.RestaurantListingRepository
import java.util.UUID

/**
 * Favorite a listing (sc-50): records the user↔listing relation. Idempotent —
 * favouriting the same listing twice is a no-op.
 *
 * The listing must exist up front (it is a foreign key on the favorite); if it
 * does not we fail fast with [ListingNotFoundException] so the web layer maps it
 * to a clean 404 instead of surfacing a DB FK violation.
 */
class FavoriteListing(
    private val favorites: FavoritesRepository,
    private val listings: RestaurantListingRepository,
) {

    fun execute(userId: UUID, listingId: UUID) {
        if (listings.findById(listingId) == null) {
            throw ListingNotFoundException(listingId)
        }
        favorites.add(userId, listingId)
    }
}
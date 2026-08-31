package com.tahirslist.application.favorite

import com.tahirslist.domain.restaurant.RestaurantListing
import java.util.UUID

/**
 * Persistence port (hexagonal "out" port) for the user↔listing favorite
 * relation. Implemented by the persistence adapter; the application layer
 * depends only on this contract.
 *
 * [add] / [remove] must be idempotent (the POST/DELETE contract is idempotent):
 * favouriting the same listing twice is a no-op, as is unfavouriting one that
 * is not favourited.
 *
 * [findFavoriteListings] returns the full [RestaurantListing] rows for the
 * user's favourites (joined), so the browse card can reuse the same
 * [com.tahirslist.bootstrap.listing.ListingReadController.BrowseCard] shape
 * with no per-row N+1.
 */
interface FavoritesRepository {
    fun add(userId: UUID, listingId: UUID)

    fun remove(userId: UUID, listingId: UUID)

    fun findFavoriteListings(userId: UUID): List<RestaurantListing>
}
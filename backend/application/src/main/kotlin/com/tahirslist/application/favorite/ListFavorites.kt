package com.tahirslist.application.favorite

import com.tahirslist.domain.restaurant.RestaurantListing
import java.util.UUID

/**
 * List the authenticated user's favourites (sc-52). Returns the favourited
 * listings as full [RestaurantListing] rows so the browse card reuses the
 * `/v1/listings` read shape with no per-row N+1.
 */
class ListFavorites(private val favorites: FavoritesRepository) {

    fun execute(userId: UUID): List<RestaurantListing> =
        favorites.findFavoriteListings(userId)
}
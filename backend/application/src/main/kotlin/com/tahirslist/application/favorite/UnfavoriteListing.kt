package com.tahirslist.application.favorite

import java.util.UUID

/**
 * Unfavorite a listing (sc-51): removes the user↔listing relation. Idempotent —
 * unfavouriting a listing that is not favourited is a no-op.
 */
class UnfavoriteListing(private val favorites: FavoritesRepository) {

    fun execute(userId: UUID, listingId: UUID) {
        favorites.remove(userId, listingId)
    }
}
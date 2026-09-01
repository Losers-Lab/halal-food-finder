package com.tahirslist.bootstrap.favorite

import com.tahirslist.application.favorite.FavoriteListing
import com.tahirslist.application.favorite.FavoritesRepository
import com.tahirslist.application.favorite.ListFavorites
import com.tahirslist.application.favorite.UnfavoriteListing
import com.tahirslist.application.listing.RestaurantListingRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Wires the framework-free favorites use cases from their ports
 * ([FavoritesRepository], [RestaurantListingRepository]), both implemented by
 * the persistence adapter. Lives in bootstrap (not web-api) so the use cases are
 * available to the authenticated [FavoritesController] that shares this module,
 * matching the existing listing/auth-surface wiring location.
 */
@Configuration
class FavoriteConfig {

    @Bean
    fun favoriteListing(
        favorites: FavoritesRepository,
        listings: RestaurantListingRepository,
    ): FavoriteListing = FavoriteListing(favorites = favorites, listings = listings)

    @Bean
    fun unfavoriteListing(favorites: FavoritesRepository): UnfavoriteListing =
        UnfavoriteListing(favorites = favorites)

    @Bean
    fun listFavorites(favorites: FavoritesRepository): ListFavorites =
        ListFavorites(favorites = favorites)
}
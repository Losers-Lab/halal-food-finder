package com.tahirslist.bootstrap.listing

import com.tahirslist.application.account.AccountRepository
import com.tahirslist.application.listing.CreateListing
import com.tahirslist.application.listing.RestaurantListingRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Wires the framework-free [CreateListing] use case from its two ports
 * ([RestaurantListingRepository], [AccountRepository]), both implemented by the
 * persistence adapter. Lives in bootstrap (not web-api) so the use case is
 * available to the authenticated [ListingController] that shares this module,
 * matching the existing auth-surface wiring location.
 */
@Configuration
class ListingConfig {

    @Bean
    fun createListing(
        listings: RestaurantListingRepository,
        accounts: AccountRepository,
    ): CreateListing = CreateListing(listings = listings, accounts = accounts)
}
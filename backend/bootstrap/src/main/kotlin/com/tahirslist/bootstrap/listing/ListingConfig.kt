package com.tahirslist.bootstrap.listing

import com.tahirslist.application.account.AccountRepository
import com.tahirslist.application.listing.CreateListing
import com.tahirslist.application.listing.RestaurantListingRepository
import com.tahirslist.application.listing.UpdateListing
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Wires the framework-free [CreateListing] and [UpdateListing] use cases from
 * their ports ([RestaurantListingRepository], [AccountRepository] for create),
 * all implemented by the persistence adapter. Lives in bootstrap (not web-api)
 * so the use cases are available to the authenticated [ListingController] that
 * shares this module, matching the existing auth-surface wiring location.
 */
@Configuration
class ListingConfig {

    @Bean
    fun createListing(
        listings: RestaurantListingRepository,
        accounts: AccountRepository,
    ): CreateListing = CreateListing(listings = listings, accounts = accounts)

    /** Owner listing edit (sc-23/47/48); only needs the listing persistence port. */
    @Bean
    fun updateListing(listings: RestaurantListingRepository): UpdateListing =
        UpdateListing(listings = listings)
}
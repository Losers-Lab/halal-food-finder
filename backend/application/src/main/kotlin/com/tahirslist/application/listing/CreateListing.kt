package com.tahirslist.application.listing

import com.tahirslist.application.account.AccountRepository
import com.tahirslist.domain.restaurant.Cuisine
import com.tahirslist.domain.restaurant.LatLng
import com.tahirslist.domain.restaurant.RestaurantListing
import java.util.UUID

/**
 * Add Listing use case. Creates a brand-new, always-**unverified** restaurant
 * listing (PRD listing-first model): anyone can add a restaurant, and it only
 * becomes verified via the separate owner-claim + certification vertical.
 *
 * Values are validated/trimmed by the domain (via [RestaurantListing.new]),
 * and the owning account must already exist. Lat/lng are required inputs here;
 * turning an address into coordinates is the separate [GeocoderPort] seam, kept
 * out of the listing write-path so a listing is always saveable independently of
 * any external geocoding provider (docs/reviews/sc-138-external-services.md §3).
 */
class CreateListing(
    private val listings: RestaurantListingRepository,
    private val accounts: AccountRepository,
) {

    fun execute(
        name: String,
        address: String,
        location: LatLng,
        cuisine: Cuisine,
        isHandCut: Boolean? = null,
        ownerId: UUID,
        alcoholServed: Boolean = false,
    ): RestaurantListing {
        // Validate/trim local input first (no I/O) via the domain factory, so bad
        // input fails fast before any repository lookup.
        val listing = RestaurantListing.new(
            name = name,
            address = address,
            location = location,
            cuisine = cuisine,
            isHandCut = isHandCut,
            ownerId = ownerId,
            alcoholServed = alcoholServed,
        )

        // Fail fast on an unknown owner before persisting anything.
        if (accounts.findById(ownerId) == null) {
            throw ListingOwnerNotFoundException(ownerId)
        }

        return listings.save(listing)
    }
}

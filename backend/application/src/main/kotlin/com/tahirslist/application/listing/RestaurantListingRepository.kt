package com.tahirslist.application.listing

import com.tahirslist.domain.restaurant.RestaurantListing
import java.util.UUID

/**
 * Persistence port (hexagonal "out" port) for restaurant listings. Implemented by
 * the persistence adapter; the application layer depends only on this contract.
 */
interface RestaurantListingRepository {
    fun save(listing: RestaurantListing): RestaurantListing
    fun findById(id: UUID): RestaurantListing?

    /**
     * All listings, for the minimal sc-157 browse/search read path. Full
     * filtered search (cuisine AND/OR, price, rating, distance) is a later
     * story; this is the unfiltered seed surface documented in
     * docs/design/sc-157-image-variants.md.
     */
    fun findAll(): List<RestaurantListing>
}

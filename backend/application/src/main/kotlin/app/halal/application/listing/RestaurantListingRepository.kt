package app.halal.application.listing

import app.halal.domain.restaurant.RestaurantListing
import java.util.UUID

/**
 * Persistence port (hexagonal "out" port) for restaurant listings. Implemented by
 * the persistence adapter; the application layer depends only on this contract.
 */
interface RestaurantListingRepository {
    fun save(listing: RestaurantListing): RestaurantListing
    fun findById(id: UUID): RestaurantListing?
}

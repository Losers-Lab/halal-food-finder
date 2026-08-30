package app.halal.domain.restaurant

import java.time.Instant
import java.util.UUID

/**
 * A restaurant listing.
 *
 * Per the PRD's listing-first model, the canonical way to create a listing is
 * [RestaurantListing.new], which always starts [VerificationStatus.UNVERIFIED]:
 * anyone can add a restaurant, and it only becomes verified via the separate
 * owner-claim + certification-verification vertical. The listing links to the
 * owning account via [ownerId]. The location is a [LatLng] stored as a PostGIS
 * `geography(Point, 4326)`.
 *
 * NOTE: ODbL share-alike on OSM/Photon-derived listing fields is an open founder
 * decision (docs/reviews/sc-138-external-services.md §5). Flagged here, not
 * decided — do not resolve it in code.
 */
data class RestaurantListing(
    val id: UUID,
    val name: String,
    val address: String,
    val location: LatLng,
    val cuisine: Cuisine,
    val cuttingMethod: CuttingMethod,
    val ownerId: UUID,
    val verificationStatus: VerificationStatus,
    val createdAt: Instant,
) {
    companion object {

        /**
         * Create a brand-new listing. Names/addresses are trimmed; blank values
         * are rejected. Always unverified (listing-first model) and timestamped now.
         *
         * @throws IllegalArgumentException if [name] or [address] is blank.
         */
        fun new(
            name: String,
            address: String,
            location: LatLng,
            cuisine: Cuisine,
            cuttingMethod: CuttingMethod,
            ownerId: UUID,
        ): RestaurantListing {
            val trimmedName = name.trim()
            val trimmedAddress = address.trim()
            require(trimmedName.isNotBlank()) { "Listing name must not be blank." }
            require(trimmedAddress.isNotBlank()) { "Listing address must not be blank." }
            return RestaurantListing(
                id = UUID.randomUUID(),
                name = trimmedName,
                address = trimmedAddress,
                location = location,
                cuisine = cuisine,
                cuttingMethod = cuttingMethod,
                ownerId = ownerId,
                verificationStatus = VerificationStatus.DEFAULT,
                createdAt = Instant.now(),
            )
        }

        /** Reconstitute a listing that was previously persisted (any status). */
        fun fromStorage(
            id: UUID,
            name: String,
            address: String,
            location: LatLng,
            cuisine: Cuisine,
            cuttingMethod: CuttingMethod,
            ownerId: UUID,
            verificationStatus: VerificationStatus,
            createdAt: Instant,
        ): RestaurantListing = RestaurantListing(
            id = id,
            name = name,
            address = address,
            location = location,
            cuisine = cuisine,
            cuttingMethod = cuttingMethod,
            ownerId = ownerId,
            verificationStatus = verificationStatus,
            createdAt = createdAt,
        )
    }
}

package com.tahirslist.domain.restaurant

import java.time.Instant
import java.util.UUID

/**
 * A restaurant listing.
 *
 * Per the PRD's listing-first model, the canonical way to create a listing is
 * [RestaurantListing.new], which always starts [VerificationStatus.UNVERIFIED]:
 * anyone can add a restaurant, and it only becomes verified via the separate
 * owner-claim + certification-verification vertical.
 *
 * [ownerId] and [cuisine] are nullable because the listing-first model admits
 * community/research seed rows with no owning account and no ratified cuisine
 * (see V6 — Omar adjudication). [RestaurantListing.new] still requires both for
 * the authenticated Add Listing flow; seed rows are reconstituted via
 * [fromStorage] with nulls. [brandId] links a location to its brand (brand /
 * location split) and [provenance] stamps the row's origin. [alcoholServed] is
 * part of the partial-halal/alcohol MVP additions (sc-118): a display attribute
 * (no search filter), defaulting to false.
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
    val cuisine: Cuisine?,
    val cuttingMethod: CuttingMethod,
    val price: Price?,
    val rating: Rating?,
    val ownerId: UUID?,
    val brandId: UUID?,
    val provenance: Provenance?,
    val verificationStatus: VerificationStatus,
    val createdAt: Instant,
    val alcoholServed: Boolean = false,
) {
    companion object {

        /**
         * Create a brand-new listing. Names/addresses are trimmed; blank values
         * are rejected. Always unverified (listing-first model) and timestamped now.
         *
         * The authenticated Add Listing flow requires a cuisine and an owning
         * account; brand/provenance are null for user-added rows.
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
            price: Price? = null,
            rating: Rating? = null,
            alcoholServed: Boolean = false,
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
                price = price,
                rating = rating,
                ownerId = ownerId,
                brandId = null,
                provenance = null,
                verificationStatus = VerificationStatus.DEFAULT,
                createdAt = Instant.now(),
                alcoholServed = alcoholServed,
            )
        }

        /**
         * Reconstitute a listing that was previously persisted (any status).
         * [cuisine], [ownerId], [brandId] and [provenance] may be null — this is
         * how unclaimed, no-cuisine community seed rows are materialised.
         */
        fun fromStorage(
            id: UUID,
            name: String,
            address: String,
            location: LatLng,
            cuisine: Cuisine?,
            cuttingMethod: CuttingMethod,
            price: Price? = null,
            rating: Rating? = null,
            ownerId: UUID?,
            brandId: UUID?,
            provenance: Provenance?,
            verificationStatus: VerificationStatus,
            createdAt: Instant,
            alcoholServed: Boolean = false,
        ): RestaurantListing = RestaurantListing(
            id = id,
            name = name,
            address = address,
            location = location,
            cuisine = cuisine,
            cuttingMethod = cuttingMethod,
            price = price,
            rating = rating,
            ownerId = ownerId,
            brandId = brandId,
            provenance = provenance,
            verificationStatus = verificationStatus,
            createdAt = createdAt,
            alcoholServed = alcoholServed,
        )
    }
}

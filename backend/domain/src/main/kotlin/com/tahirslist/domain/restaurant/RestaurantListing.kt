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
 * location split) and [provenance] stamps the row's origin.
 *
 * [isHandCut] is the founder's sc-42 ruling: there is NO machine-cut concept.
 * Hand-cut is a plain boolean — a listing either claims it or not. null means
 * "unknown / not claimed" (community/research seed rows, which never claim a
 * method); true means hand-cut (Zabiha); false means not-hand-cut. Search treats
 * null the same as false: only a hand-cut-only filter excludes it. This replaces
 * the earlier either/or CuttingMethod enum (HAND_CUT | MACHINE_CUT) wholesale
 * (V17 migrates the column).
 *
 * Partial-halal modeling (sc-119, founder re-scope):
 *  - [halalScope] + [halalItems] model *which* items are halal per listing. This
 *    is **orthogonal** to [verificationStatus]: a PARTIALLY_HALAL place may still
 *    be VERIFIED (it can hold a certificate for the halal portion).
 *  - [crossContamination] is a HARD index gate: only
 *    [CrossContamination.NO_CROSS_CONTAMINATION] qualifies a listing for the
 *    search index (see CrossContamination.isIndexQualified).
 *  - [alcoholServed] is a display attribute (sc-118), part of the
 *    partial-halal/alcohol MVP additions; it is a display attribute (no search
 *    filter), defaulting to false.
 *
 * [isDelivery] is the sc-184 service-mode field, modelled on the same sc-42
 * tri-state boolean pattern: delivery is an EXTRA on/off flag a listing claims
 * or not, NOT an enum of service modes. null means "unknown / not claimed"
 * (community/research seed rows); true means offers delivery; false means no
 * delivery (pickup-only). Search treats null the same as false: only a
 * delivery-only filter excludes it. Sc-184 stays deliberately lean (pickup is
 * the implicit baseline default), so a listing claims delivery explicitly and
 * pickup is the absence of that claim — the frontend child story surfaces this
 * dimension on cards/detail and wires a deliveryOnly search filter.
 *
 * Structured address (sc-187 founder re-scope #2): [address] remains the street
 * line ("3885 Belt Line Rd") for backward compat; [city], [province], [postal]
 * and [country] model the rest of the address so the detail page can render the
 * full comma-form address. They are nullable — legacy/community rows may predate
 * the structured fields (V7 seeds exist without them) — and province is a
 * country-agnostic field (works for both CA/ON and US/TX, per Aisha's seed data).
 * When provided they are trimmed and length-capped; a blank value is rejected
 * (a caller signals "unknown" with null, never whitespace).
 *
 * NOTE: ODbL share-alike on OSM/Photon-derived listing fields is an open founder
 * decision (docs/reviews/sc-138-external-services.md §5). Flagged here, not
 * decided — do not resolve it in code.
 */
data class RestaurantListing(
    val id: UUID,
    val name: String,
    val address: String,
    val city: String? = null,
    val province: String? = null,
    val postal: String? = null,
    val country: String? = null,
    val location: LatLng,
    val cuisine: Cuisine?,
    val isHandCut: Boolean?,
    val isDelivery: Boolean?,
    val price: Price?,
    val rating: Rating?,
    val ownerId: UUID?,
    val brandId: UUID?,
    val provenance: Provenance?,
    val verificationStatus: VerificationStatus,
    val createdAt: Instant,
    val halalScope: HalalScope = HalalScope.DEFAULT,
    val halalItems: Set<HalalItem> = emptySet(),
    val crossContamination: CrossContamination = CrossContamination.DEFAULT,
    val alcoholServed: Boolean = false,
) {

    /**
     * Produce an updated copy of this listing's *editable content fields*
     * (sc-23/47/48 owner listing edit). Unlike [new], identity and governance
     * fields — [id], [ownerId], [brandId], [provenance], [verificationStatus],
     * [createdAt], [price] and [rating] — are PRESERVED untouched: an owner
     * editing their listing can never change who owns it, its verification
     * status, or audit fields. Ownership/status changes are out of scope for a
     * listing edit (they run through the claim/verification vertical).
     *
     * Names/addresses are trimmed; blank values are rejected (same contract
     * as [new]). [isHandCut] / [isDelivery] keep their tri-state (null =
     * unknown / not claimed) semantics — an owner may explicitly clear them.
     *
     * The structured-address fields ([city]/[province]/[postal]/[country]) are
     * carried through unchanged by this edit — sc-187's owner-facing address
     * editing is out of scope for owner listing edit (sc-23); they are only
     * populated on create / from storage.
     *
     * @throws IllegalArgumentException if [name] or [address] is blank.
     */
    fun withUpdatedFields(
        name: String,
        address: String,
        location: LatLng,
        cuisine: Cuisine?,
        isHandCut: Boolean?,
        isDelivery: Boolean?,
        halalScope: HalalScope = HalalScope.DEFAULT,
        halalItems: Set<HalalItem> = emptySet(),
        crossContamination: CrossContamination = CrossContamination.DEFAULT,
        alcoholServed: Boolean = false,
    ): RestaurantListing {
        val trimmedName = name.trim()
        val trimmedAddress = address.trim()
        require(trimmedName.isNotBlank()) { "Listing name must not be blank." }
        require(trimmedAddress.isNotBlank()) { "Listing address must not be blank." }
        return copy(
            name = trimmedName,
            address = trimmedAddress,
            location = location,
            cuisine = cuisine,
            isHandCut = isHandCut,
            isDelivery = isDelivery,
            halalScope = halalScope,
            halalItems = halalItems,
            crossContamination = crossContamination,
            alcoholServed = alcoholServed,
        )
    }

    companion object {

        /** sc-187 structured-address max lengths (mirror the V20 VARCHAR widths). */
        const val CITY_MAX_LENGTH: Int = 128
        const val PROVINCE_MAX_LENGTH: Int = 64
        const val POSTAL_MAX_LENGTH: Int = 32
        const val COUNTRY_MAX_LENGTH: Int = 16

        /**
         * Trim + validate an optional structured-address field. null (unknown)
         * is preserved; a provided value must be non-blank after trimming and
         * within [maxLength].
         *
         * @throws IllegalArgumentException if [value] is blank after trim or too long.
         */
        private fun trimAddressField(field: String, value: String?, maxLength: Int): String? {
            if (value == null) return null
            val trimmed = value.trim()
            require(trimmed.isNotBlank()) { "Listing $field must not be blank." }
            require(trimmed.length <= maxLength) { "Listing $field must be $maxLength characters or fewer." }
            return trimmed
        }

        /**
         * Create a brand-new listing. Names/addresses are trimmed; blank values
         * are rejected. Always unverified (listing-first model) and timestamped now.
         *
         * The authenticated Add Listing flow requires a cuisine and an owning
         * account; brand/provenance are null for user-added rows.
         *
         * @throws IllegalArgumentException if [name] or [address] is blank, or any
         *   provided structured-address field ([city]/[province]/[postal]/[country])
         *   is blank after trim or exceeds its length cap.
         */
        fun new(
            name: String,
            address: String,
            city: String? = null,
            province: String? = null,
            postal: String? = null,
            country: String? = null,
            location: LatLng,
            cuisine: Cuisine,
            isHandCut: Boolean? = null,
            isDelivery: Boolean? = null,
            ownerId: UUID,
            price: Price? = null,
            rating: Rating? = null,
            halalScope: HalalScope = HalalScope.DEFAULT,
            halalItems: Set<HalalItem> = emptySet(),
            crossContamination: CrossContamination = CrossContamination.DEFAULT,
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
                city = trimAddressField("city", city, CITY_MAX_LENGTH),
                province = trimAddressField("province", province, PROVINCE_MAX_LENGTH),
                postal = trimAddressField("postal", postal, POSTAL_MAX_LENGTH),
                country = trimAddressField("country", country, COUNTRY_MAX_LENGTH),
                location = location,
                cuisine = cuisine,
                isHandCut = isHandCut,
                isDelivery = isDelivery,
                price = price,
                rating = rating,
                ownerId = ownerId,
                brandId = null,
                provenance = null,
                verificationStatus = VerificationStatus.DEFAULT,
                createdAt = Instant.now(),
                halalScope = halalScope,
                halalItems = halalItems,
                crossContamination = crossContamination,
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
            city: String? = null,
            province: String? = null,
            postal: String? = null,
            country: String? = null,
            location: LatLng,
            cuisine: Cuisine?,
            isHandCut: Boolean? = null,
            isDelivery: Boolean? = null,
            price: Price? = null,
            rating: Rating? = null,
            ownerId: UUID?,
            brandId: UUID?,
            provenance: Provenance?,
            verificationStatus: VerificationStatus,
            createdAt: Instant,
            halalScope: HalalScope = HalalScope.DEFAULT,
            halalItems: Set<HalalItem> = emptySet(),
            crossContamination: CrossContamination = CrossContamination.DEFAULT,
            alcoholServed: Boolean = false,
        ): RestaurantListing = RestaurantListing(
            id = id,
            name = name,
            address = address,
            city = trimAddressField("city", city, CITY_MAX_LENGTH),
            province = trimAddressField("province", province, PROVINCE_MAX_LENGTH),
            postal = trimAddressField("postal", postal, POSTAL_MAX_LENGTH),
            country = trimAddressField("country", country, COUNTRY_MAX_LENGTH),
            location = location,
            cuisine = cuisine,
            isHandCut = isHandCut,
            isDelivery = isDelivery,
            price = price,
            rating = rating,
            ownerId = ownerId,
            brandId = brandId,
            provenance = provenance,
            verificationStatus = verificationStatus,
            createdAt = createdAt,
            halalScope = halalScope,
            halalItems = halalItems,
            crossContamination = crossContamination,
            alcoholServed = alcoholServed,
        )
    }
}
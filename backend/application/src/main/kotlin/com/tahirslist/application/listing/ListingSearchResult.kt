package com.tahirslist.application.listing

import com.tahirslist.domain.restaurant.Cuisine
import com.tahirslist.domain.restaurant.HalalScope
import com.tahirslist.domain.restaurant.LatLng
import com.tahirslist.domain.restaurant.Rating
import com.tahirslist.domain.restaurant.VerificationStatus
import java.util.UUID

/**
 * A single location-search hit: one listing plus its straight-line distance in
 * miles from the search centre. Carries the full card fields so the public read
 * surface can render a card without re-joining to the source listing.
 *
 * Sc-10 contract: distance is straight-line (great-circle) miles, computed by
 * PostGIS (ST_DistanceSphere) against the denormalised `listing_search` table.
 *
 * [isHandCut] is nullable: null = unknown / not claimed (seed rows); true =
 * hand-cut; false = not hand-cut (sc-42 — no machine-cut concept). [halalScope]
 * is exposed so the search card can render the halal-coverage disclosure
 * alongside the verification badge without overstating full halal-ness
 * (verification is independent of scope — sc-119).
 *
 * [isDelivery] is nullable on the same sc-42 pattern (sc-184): null = unknown /
 * not claimed (seed rows); true = offers delivery; false = no delivery
 * (pickup-only). Pickup is the implicit baseline default; delivery is the extra
 * flag a listing claims.
 */
data class ListingSearchResult(
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
    val halalScope: HalalScope,
    val isDelivery: Boolean?,
    val verificationStatus: VerificationStatus,
    val rating: Rating?,
    val distanceMiles: Double,
)
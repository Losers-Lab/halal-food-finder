package com.tahirslist.application.listing

import com.tahirslist.domain.restaurant.Cuisine
import com.tahirslist.domain.restaurant.CuttingMethod
import com.tahirslist.domain.restaurant.LatLng
import com.tahirslist.domain.restaurant.VerificationStatus
import java.util.UUID

/**
 * A single location-search hit: one listing plus its straight-line distance in
 * miles from the search centre. Carries the full card fields so the public read
 * surface can render a card without re-joining to the source listing.
 *
 * Sc-10 contract: distance is straight-line (great-circle) miles, computed by
 * PostGIS (ST_DistanceSphere) against the denormalised `listing_search` table.
 */
data class ListingSearchResult(
    val id: UUID,
    val name: String,
    val address: String,
    val location: LatLng,
    val cuisine: Cuisine?,
    val cuttingMethod: CuttingMethod,
    val verificationStatus: VerificationStatus,
    val distanceMiles: Double,
)
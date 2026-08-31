package com.tahirslist.application.listing

import com.tahirslist.domain.restaurant.LatLng

/**
 * Persistence port ("out" port, hexagonal) for sc-10 location search. Returns
 * listings within [radiusMiles] of [center], ordered by straight-line distance
 * ascending. Offset paging. Implemented by the persistence adapter (PostGIS).
 *
 * [cuttingMethod] (sc-42) narrows the results to listings whose stored method is
 * HAND_CUT or MACHINE_CUT; [CuttingMethodFilter.BOTH] (the default) is the
 * "any" filter and matches every listing regardless of its stored method.
 */
interface ListingSearchQuery {
    fun searchNearby(
        center: LatLng,
        radiusMiles: Double,
        cuttingMethod: CuttingMethodFilter = CuttingMethodFilter.BOTH,
        offset: Int,
        limit: Int,
    ): List<ListingSearchResult>
}
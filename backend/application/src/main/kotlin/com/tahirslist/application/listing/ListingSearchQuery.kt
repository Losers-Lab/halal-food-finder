package com.tahirslist.application.listing

import com.tahirslist.domain.restaurant.LatLng

/**
 * Persistence port ("out" port, hexagonal) for sc-10 location search. Returns
 * listings within [radiusMiles] of [center], ordered by straight-line distance
 * ascending. Offset paging. Implemented by the persistence adapter (PostGIS).
 */
interface ListingSearchQuery {
    fun searchNearby(
        center: LatLng,
        radiusMiles: Double,
        offset: Int,
        limit: Int,
    ): List<ListingSearchResult>
}
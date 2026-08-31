package com.tahirslist.application.listing

import com.tahirslist.domain.restaurant.LatLng

/**
 * Persistence port ("out" port, hexagonal) for sc-10 location search. Returns
 * listings within [radiusMiles] of [center], ordered by straight-line distance
 * ascending. Offset paging. Implemented by the persistence adapter (PostGIS).
 *
 * [filters] (sc-42 cutting method, sc-43 price range, sc-44 cuisine AND/OR)
 * narrow the result set; absent filter values match everything. The default
 * empty [ListingSearchFilters] is exactly the location-only search.
 */
interface ListingSearchQuery {
    fun searchNearby(
        center: LatLng,
        radiusMiles: Double,
        filters: ListingSearchFilters = ListingSearchFilters(),
        offset: Int,
        limit: Int,
    ): List<ListingSearchResult>
}
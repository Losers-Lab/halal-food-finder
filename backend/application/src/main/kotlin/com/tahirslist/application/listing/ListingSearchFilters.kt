package com.tahirslist.application.listing

import java.math.BigDecimal

/**
 * The optional narrowing filters for location search (sc-42 cutting method,
 * sc-43 price range, sc-44 cuisine AND/OR, sc-45 minimum rating). A filter value
 * carried here has already been validated/parsed at the edge ([CuttingMethodFilter]
 * vocabulary, [CuisineLogic] vocabulary, non-negative price bounds, 0..5 rating,
 * normalized cuisine strings) so the persistence port stays a pure query.
 *
 * Absent values match everything (no narrowing): [CuttingMethodFilter.BOTH] is
 * "any method", an empty [cuisines] is "any cuisine" (no cuisine predicate), a
 * null [minPrice]/[maxPrice] is "any price", and a null [minRating] is "any
 * rating".
 */
data class ListingSearchFilters(
    val cuttingMethod: CuttingMethodFilter = CuttingMethodFilter.BOTH,
    val cuisines: List<String> = emptyList(),
    val cuisineLogic: CuisineLogic = CuisineLogic.OR,
    val minPrice: BigDecimal? = null,
    val maxPrice: BigDecimal? = null,
    val minRating: BigDecimal? = null,
)
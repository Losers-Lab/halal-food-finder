package com.tahirslist.application.listing

import java.math.BigDecimal

/**
 * The optional narrowing filters for location search (sc-42 cutting method,
 * sc-43 price range, sc-44 cuisine AND/OR). A filter value carried here has
 * already been validated/parsed at the edge ([CuttingMethodFilter] vocabulary,
 * [CuisineLogic] vocabulary, non-negative price bounds, normalized cuisine
 * strings) so the persistence port stays a pure query.
 *
 * Absent values match everything (no narrowing): [CuttingMethodFilter.BOTH] is
 * "any method", an empty [cuisines] is "any cuisine" (no cuisine predicate), and
 * null [minPrice]/[maxPrice] is "any price".
 */
data class ListingSearchFilters(
    val cuttingMethod: CuttingMethodFilter = CuttingMethodFilter.BOTH,
    val cuisines: List<String> = emptyList(),
    val cuisineLogic: CuisineLogic = CuisineLogic.OR,
    val minPrice: BigDecimal? = null,
    val maxPrice: BigDecimal? = null,
)
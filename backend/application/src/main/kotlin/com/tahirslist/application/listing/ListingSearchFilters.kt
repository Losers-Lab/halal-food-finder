package com.tahirslist.application.listing

import java.math.BigDecimal

/**
 * The optional narrowing filters for location search (sc-42 hand-cut,
 * sc-43 price range, sc-44 cuisine AND/OR, sc-45 minimum rating). A filter value
 * carried here has already been validated/parsed at the edge ([CuisineLogic]
 * vocabulary, non-negative price bounds, 0..5 rating, normalized cuisine strings)
 * so the persistence port stays a pure query.
 *
 * Absent values match everything (no narrowing): [handCutOnly] = false is "any
 * cutting status" (no hand-cut predicate), an empty [cuisines] is "any cuisine"
 * (no cuisine predicate), a null [minPrice]/[maxPrice] is "any price", and a
 * null [minRating] is "any rating".
 *
 * [handCutOnly] is the founder's sc-42 ruling: hand-cut is an EXTRA on/off
 * boolean filter, NOT an either/or choice. When true, only listings that claim
 * hand-cut (is_hand_cut = true) match; when false (default) the predicate is
 * omitted entirely and every listing matches — including those whose hand-cut
 * status is unknown (null) or not-hand-cut. There is no machine-cut concept.
 */
data class ListingSearchFilters(
    val handCutOnly: Boolean = false,
    val cuisines: List<String> = emptyList(),
    val cuisineLogic: CuisineLogic = CuisineLogic.OR,
    val minPrice: BigDecimal? = null,
    val maxPrice: BigDecimal? = null,
    val minRating: BigDecimal? = null,
)
package com.tahirslist.domain.restaurant

import java.math.BigDecimal

/**
 * A restaurant listing's rating (sc-45): the value the search's `minRating`
 * filter ranges over. Always on the 0..5 scale so an out-of-range value can
 * never reach the database.
 *
 * Following [Price]'s value-object discipline: bounded and validated at the
 * boundary; a [Rating] that exists is a legal rating. NULL at the persistence
 * layer means "no rating known" and is excluded from the rating filter — the
 * same null semantics [Cuisine] (V6) and [Price] (V9) already document. Ratings
 * are enrichment data (Google/Yelp U-10) with an owner/user-entered fallback;
 * the exact writer is a later story, but the stored value is always 0..5.
 */
data class Rating(val value: BigDecimal) {

    init {
        require(value >= BigDecimal.ZERO) { "Rating must not be negative." }
        require(value <= MAX) { "Rating must be $MAX or fewer." }
    }

    companion object {
        /** Upper bound of the 0..5 rating scale (inclusive). */
        val MAX: BigDecimal = BigDecimal("5.00")
    }
}

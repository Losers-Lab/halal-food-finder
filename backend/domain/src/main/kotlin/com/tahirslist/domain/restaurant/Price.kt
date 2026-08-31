package com.tahirslist.domain.restaurant

import java.math.BigDecimal

/**
 * A restaurant listing's price point (sc-43): the value the location-search price
 * filter ranges over. Always positive and bounded so a runaway value can never
 * reach the database.
 *
 * Following [Cuisine]'s value-object discipline: free-form but validated at the
 * boundary; a [Price] that exists is a legal price. NULL at the persistence
 * layer means "no price known" and is excluded from price filters — the same
 * null semantics [Cuisine] already documents for cuisine filters (V6).
 */
data class Price(val value: BigDecimal) {

    init {
        require(value > BigDecimal.ZERO) { "Price must be positive." }
        require(value <= MAX) { "Price must be $MAX or fewer." }
    }

    companion object {
        /** Upper bound — far beyond any real meal price; guards arithmetic/DB overflow. */
        val MAX: BigDecimal = BigDecimal("100000")
    }
}
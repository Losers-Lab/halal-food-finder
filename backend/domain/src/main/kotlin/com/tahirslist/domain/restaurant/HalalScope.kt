package com.tahirslist.domain.restaurant

/**
 * The per-listing halal *coverage* disclosure, orthogonal to
 * [VerificationStatus]. A place that is verified may still be only partially
 * halal (founder sc-119 re-scope: a partial-halal place CAN be verified — it may
 * hold a certificate for what it does serve as halal).
 *
 * The read/verification tag uses this scope so a "Verified" badge never implies
 * the entire restaurant is halal (amanah / trust-language requirement).
 */
enum class HalalScope {
    /** The whole menu/establishment is halal. */
    FULLY_HALAL,

    /** Only some items are halal — see `halalItems` on the listing. */
    PARTIALLY_HALAL,

    /** Unknown / not yet disclosed. */
    NOT_DISCLOSED,
    ;

    companion object {
        /** The status every newly created listing starts with. */
        val DEFAULT: HalalScope = NOT_DISCLOSED
    }
}
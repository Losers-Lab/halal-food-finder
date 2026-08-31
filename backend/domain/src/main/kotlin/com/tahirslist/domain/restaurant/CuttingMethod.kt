package com.tahirslist.domain.restaurant

/**
 * How the restaurant's meat is butchered — the product's flagship filter
 * (matching the PRD's hand-cut / machine-cut distinction). A restaurant's
 * stored value is either HAND_CUT or MACHINE_CUT; UNSPECIFIED ("any") is what a
 * listing records when the method is unknown or not claimed, and is what search
 * surfaces when the user has not filtered.
 */
enum class CuttingMethod {
    HAND_CUT,
    MACHINE_CUT,
    UNSPECIFIED,
    ;
}

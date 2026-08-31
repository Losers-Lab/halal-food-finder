package com.tahirslist.application.listing

/**
 * The food-cutting-method filter for location search (sc-42): how the user asks
 * the search to narrow by how the meat is cut.
 *
 * Deliberately distinct from the per-listing domain attribute
 * [com.tahirslist.domain.restaurant.CuttingMethod], which carries the third
 * recording value `UNSPECIFIED` for a listing that does not claim a method.
 * A filter never means "unspecified" — the user's three choices are literally
 * HAND_CUT, MACHINE_CUT, or [BOTH]. [BOTH] ("any") matches every listing
 * regardless of its stored method and is exactly the "no filter" request,
 * keeping absent vs BOTH semantics identical on the wire.
 */
enum class CuttingMethodFilter {
    HAND_CUT,
    MACHINE_CUT,
    BOTH,
    ;
}
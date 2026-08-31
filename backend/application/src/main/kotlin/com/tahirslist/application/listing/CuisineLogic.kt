package com.tahirslist.application.listing

/**
 * How multiple selected cuisines are combined (sc-44). Default logic is [OR]
 * (the PRD edge case): a listing matches if it has ANY of the selected cuisines.
 * [AND] matches only a listing that has ALL of the selected cuisines — i.e. a
 * multi-cuisine listing — so AND is only meaningful against the multi-cuisine
 * `restaurant_listing_cuisines` store.
 */
enum class CuisineLogic {
    AND,
    OR,
}
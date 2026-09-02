package com.tahirslist.persistence.listing

import com.tahirslist.application.listing.CuisineLogic
import com.tahirslist.application.listing.ListingSearchFilters
import com.tahirslist.application.listing.ListingSearchQuery
import com.tahirslist.application.listing.ListingSearchResult
import com.tahirslist.domain.restaurant.Cuisine
import com.tahirslist.domain.restaurant.HalalScope
import com.tahirslist.domain.restaurant.LatLng
import com.tahirslist.domain.restaurant.Rating
import com.tahirslist.domain.restaurant.VerificationStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet

/** One statute mile in metres — ST_DistanceSphere returns metres. */
private const val METRES_PER_MILE = 1609.344

/**
 * JDBC implementation of [ListingSearchQuery] against the denormalised
 * `listing_search` table (see V8__create_listing_search.sql, extended by
 * V18__sc_119_partial_halal.sql).
 *
 * Exactly the ratified sc-10 contract in one query: `ST_DWithin` (geography,
 * metres) filters to the radius, `ST_DistanceSphere` (geometry, metres) orders
 * ascending, offset/limit do the paging. Because the stored column is
 * `geography(Point,4326)`, the centre is built as a geography for the filter
 * and cast to geometry for the sphere distance; the returned distance is
 * converted metres -> statute miles (/1609.344).
 *
 * sc-119:
 *  - CROSS-CONTAMINATION INDEX GATE: the query always constrains
 *    `cross_contamination = 'NO_CROSS_CONTAMINATION'` as defence-in-depth (the
 *    mirror already only indexes qualified rows). PRESENT/UNCERTAIN listings can
 *    never appear in search.
 *  - The hand-cut filter ([ListingSearchFilters.handCutOnly]) maps to
 *    `is_hand_cut = true` when set; absent = any (sc-42, no machine-cut).
 *
 * radius <= 0 would return nothing meaningfully and is a caller error; the
 * controller guards it (IllegalArgumentException -> 400).
 */
@Repository
class JdbcListingSearchQuery(private val jdbc: JdbcTemplate) : ListingSearchQuery {

    override fun searchNearby(
        center: LatLng,
        radiusMiles: Double,
        filters: ListingSearchFilters,
        offset: Int,
        limit: Int,
    ): List<ListingSearchResult> {
        val radiusMeters = radiusMiles * METRES_PER_MILE
        // Non-positive radius cannot match anything; the DB-boundary test pins that
        // a 0 radius yields an empty result rather than an error.
        if (radiusMeters <= 0.0) return emptyList()

        // sc-42: hand-cut is an EXTRA on/off boolean filter. When off (default)
        // no predicate is added so every listing matches regardless of status;
        // when on, a simple IS TRUE predicate (ignores NULL = unknown, which
        // search treats as not-hand-cut — same null semantics as price/rating).
        val handCutClause = if (filters.handCutOnly) "AND is_hand_cut" else ""

        // sc-44: cuisine AND/OR over the multi-cuisine join table.
        //   OR (default): the listing has ANY selected cuisine -> EXISTS.
        //   AND: the listing has ALL selected cuisines -> COUNT(DISTINCT matched) == selected count.
        // A listing with no cuisine rows (NULL-cuisine seed) never matches either
        // clause, so cuisine filters exclude NULL-cuisine listings (V6 contract).
        // Values are normalized at the edge; we normalize here too so direct
        // callers and repeated builds get the same idempotent result.
        val minPrice = filters.minPrice
        val maxPrice = filters.maxPrice
        val normalizedCuisines = filters.cuisines.map { it.trim().lowercase() }.distinct()
        val cuisineClause = if (normalizedCuisines.isEmpty()) {
            ""
        } else {
            val placeholders = normalizedCuisines.joinToString(", ") { "?" }
            if (filters.cuisineLogic == CuisineLogic.AND) {
                "AND (SELECT COUNT(DISTINCT rc.cuisine) FROM restaurant_listing_cuisines rc " +
                    "WHERE rc.listing_id = listing_search.id AND rc.cuisine IN ($placeholders)) = ${normalizedCuisines.size}"
            } else {
                "AND EXISTS (SELECT 1 FROM restaurant_listing_cuisines rc " +
                    "WHERE rc.listing_id = listing_search.id AND rc.cuisine IN ($placeholders))"
            }
        }

        // sc-43: price range against the denormalised price mirror. A NULL-price
        // row never satisfies >= / <= and is therefore excluded from a price filter,
        // the same null semantics cuisine filters already use (V6).
        val priceClause = buildString {
            if (filters.minPrice != null) append("AND listing_search.price >= ? ")
            if (filters.maxPrice != null) append("AND listing_search.price <= ? ")
        }.trimEnd()

        // sc-45: minimum rating against the denormalised rating mirror. A
        // NULL-rating row never satisfies >= and is therefore excluded from a
        // rating filter, the same null semantics price (V9) and cuisine (V6)
        // already use.
        val minRating = filters.minRating
        val ratingClause = if (minRating != null) "AND listing_search.rating >= ?" else ""

        val sql = """
            SELECT
                id,
                name,
                address,
                ST_Y(location::geometry) AS lat,
                ST_X(location::geometry) AS lng,
                cuisine,
                is_hand_cut,
                halal_scope,
                verification_status,
                rating,
                ST_DistanceSphere(
                    location::geometry,
                    ST_SetSRID(ST_MakePoint(?, ?), 4326)
                ) / $METRES_PER_MILE AS distance_miles
            FROM listing_search
            WHERE ST_DWithin(
                location,
                ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography,
                ?
            )
            AND cross_contamination = 'NO_CROSS_CONTAMINATION'
            $handCutClause
            $cuisineClause
            $priceClause
            $ratingClause
            ORDER BY ST_DistanceSphere(
                location::geometry,
                ST_SetSRID(ST_MakePoint(?, ?), 4326)
            )
            LIMIT ? OFFSET ?
        """.trimIndent()

        val args = mutableListOf<Any>(
            // distance/order centre (x=lng, y=lat)
            center.lng,
            center.lat,
            // filter centre
            center.lng,
            center.lat,
            radiusMeters,
        )
        args.addAll(normalizedCuisines)
        if (minPrice != null) args.add(minPrice)
        if (maxPrice != null) args.add(maxPrice)
        if (minRating != null) args.add(minRating)
        args.addAll(listOf(center.lng, center.lat, limit, offset))

        return jdbc.query(sql, { rs, _ -> rs.toSearchResult() }, *args.toTypedArray())
    }

    private fun ResultSet.toSearchResult(): ListingSearchResult = ListingSearchResult(
        id = getObject("id", java.util.UUID::class.java),
        name = getString("name"),
        address = getString("address"),
        location = LatLng(lat = getDouble("lat"), lng = getDouble("lng")),
        cuisine = getString("cuisine")?.let { Cuisine(it) },
        isHandCut = getObject("is_hand_cut", java.lang.Boolean::class.java) as Boolean?,
        halalScope = HalalScope.valueOf(getString("halal_scope")),
        verificationStatus = VerificationStatus.valueOf(getString("verification_status")),
        rating = getBigDecimal("rating")?.let { Rating(it) },
        distanceMiles = getDouble("distance_miles"),
    )
}
package com.tahirslist.persistence.listing

import com.tahirslist.application.listing.CuttingMethodFilter
import com.tahirslist.application.listing.ListingSearchQuery
import com.tahirslist.application.listing.ListingSearchResult
import com.tahirslist.domain.restaurant.Cuisine
import com.tahirslist.domain.restaurant.CuttingMethod
import com.tahirslist.domain.restaurant.LatLng
import com.tahirslist.domain.restaurant.VerificationStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet

/** One statute mile in metres — ST_DistanceSphere returns metres. */
private const val METRES_PER_MILE = 1609.344

/**
 * JDBC implementation of [ListingSearchQuery] against the denormalised
 * `listing_search` table (see V8__create_listing_search.sql).
 *
 * Exactly the ratified sc-10 contract in one query: `ST_DWithin` (geography,
 * metres) filters to the radius, `ST_DistanceSphere` (geometry, metres) orders
 * ascending, offset/limit do the paging. Because the stored column is
 * `geography(Point,4326)`, the centre is built as a geography for the filter
 * and cast to geometry for the sphere distance; the returned distance is
 * converted metres -> statute miles (/1609.344).
 *
 * radius <= 0 would return nothing meaningfully and is a caller error; the
 * controller guards it (IllegalArgumentException -> 400).
 */
@Repository
class JdbcListingSearchQuery(private val jdbc: JdbcTemplate) : ListingSearchQuery {

    override fun searchNearby(
        center: LatLng,
        radiusMiles: Double,
        cuttingMethod: CuttingMethodFilter,
        offset: Int,
        limit: Int,
    ): List<ListingSearchResult> {
        val radiusMeters = radiusMiles * METRES_PER_MILE
        // Non-positive radius cannot match anything; the DB-boundary test pins that
        // a 0 radius yields an empty result rather than an error.
        if (radiusMeters <= 0.0) return emptyList()

        // sc-42: BOTH ("any") adds no predicate so every stored method matches;
        // HAND_CUT / MACHINE_CUT add an equality predicate on the stored column.
        val cuttingClause = if (cuttingMethod == CuttingMethodFilter.BOTH) {
            ""
        } else {
            "AND cutting_method = ?"
        }

        val sql = """
            SELECT
                id,
                name,
                address,
                ST_Y(location::geometry) AS lat,
                ST_X(location::geometry) AS lng,
                cuisine,
                cutting_method,
                verification_status,
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
            $cuttingClause
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
        if (cuttingMethod != CuttingMethodFilter.BOTH) args.add(cuttingMethod.name)
        args.addAll(listOf(center.lng, center.lat, limit, offset))

        return jdbc.query(sql, { rs, _ -> rs.toSearchResult() }, *args.toTypedArray())
    }

    private fun ResultSet.toSearchResult(): ListingSearchResult = ListingSearchResult(
        id = getObject("id", java.util.UUID::class.java),
        name = getString("name"),
        address = getString("address"),
        location = LatLng(lat = getDouble("lat"), lng = getDouble("lng")),
        cuisine = getString("cuisine")?.let { Cuisine(it) },
        cuttingMethod = CuttingMethod.valueOf(getString("cutting_method")),
        verificationStatus = VerificationStatus.valueOf(getString("verification_status")),
        distanceMiles = getDouble("distance_miles"),
    )
}
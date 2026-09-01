package com.tahirslist.persistence.listing

import com.tahirslist.application.listing.RestaurantListingRepository
import com.tahirslist.domain.restaurant.Cuisine
import com.tahirslist.domain.restaurant.LatLng
import com.tahirslist.domain.restaurant.Price
import com.tahirslist.domain.restaurant.Provenance
import com.tahirslist.domain.restaurant.Rating
import com.tahirslist.domain.restaurant.RestaurantListing
import com.tahirslist.domain.restaurant.VerificationStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.support.TransactionTemplate
import java.sql.ResultSet
import java.util.UUID

/**
 * JDBC implementation of the [RestaurantListingRepository] port against the
 * `restaurant_listings` table (see V4__create_restaurant_listings.sql). Spring
 * JDBC only — deliberately boring and explicit. The database owns id generation.
 *
 * A location point is written with `ST_SetSRID(ST_MakePoint(lng, lat), 4326)`
 * (ST_MakePoint takes x=longitude first) and cast to the `geography(Point,4326)`
 * column; it is read back by casting the geography to geometry so ST_X/ST_Y
 * yield longitude/latitude.
 *
 * [save] writes the denormalised `listing_search` projection in the SAME
 * transaction, so a newly-added listing is immediately searchable (sc-10) and
 * the two tables can never diverge on this write path.
 */
@Repository
class JdbcRestaurantListingRepository(
    private val jdbc: JdbcTemplate,
    private val tx: TransactionTemplate,
) : RestaurantListingRepository {

    override fun save(listing: RestaurantListing): RestaurantListing {
        val saved: RestaurantListing = tx.execute {
            val id = jdbc.queryForObject(
                """
                INSERT INTO restaurant_listings (name, address, location, cuisine, is_hand_cut, owner_id, brand_id, provenance, verification_status, price, rating, alcohol_served)
                VALUES (
                    ?, ?,
                    ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography,
                    ?, ?, ?, ?, ?, ?, ?, ?, ?
                )
                RETURNING id
                """.trimIndent(),
                UUID::class.java,
                listing.name,
                listing.address,
                listing.location.lng, // ST_MakePoint(x = longitude, y = latitude)
                listing.location.lat,
                listing.cuisine?.value,
                listing.isHandCut,
                listing.ownerId,
                listing.brandId,
                listing.provenance?.value,
                listing.verificationStatus.name,
                listing.price?.value,
                listing.rating?.value,
                listing.alcoholServed,
            ) ?: error("INSERT RETURNING id returned no row")

            val withId = listing.copy(id = id)
            // Multi-cuisine store (sc-44): the single Add-Listing cuisine becomes
            // one row, so user-added listings are immediately cuisine-filterable.
            withId.cuisine?.let {
                jdbc.update(
                    """
                    INSERT INTO restaurant_listing_cuisines (listing_id, cuisine)
                    VALUES (?, ?)
                    ON CONFLICT (listing_id, cuisine) DO NOTHING
                    """.trimIndent(),
                    id, it.value,
                )
            }
            mirrorIntoListingSearch(withId)
            withId
        } ?: error("transaction returned no listing")

        return saved
    }

    /**
     * Denormalised read-model mirror (V8): the search surface reads ONLY
     * `listing_search`. Keeping it in the same transaction as the source write
     * guarantees a listing is either in both tables or neither — never only the
     * source. Partial-failure of either INSERT rolls the whole save back.
     */
    private fun mirrorIntoListingSearch(listing: RestaurantListing) {
        jdbc.update(
            """
            INSERT INTO listing_search (id, name, address, location, cuisine, is_hand_cut, verification_status, price, rating, alcohol_served)
            VALUES (?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            listing.id,
            listing.name,
            listing.address,
            listing.location.lng,
            listing.location.lat,
            listing.cuisine?.value,
            listing.isHandCut,
            listing.verificationStatus.name,
            listing.price?.value,
            listing.rating?.value,
            listing.alcoholServed,
        )
    }

    override fun findById(id: UUID): RestaurantListing? {
        val rows = jdbc.query(
            """
            SELECT
                id,
                name,
                address,
                ST_Y(location::geometry) AS lat,
                ST_X(location::geometry) AS lng,
                cuisine,
                is_hand_cut,
                price,
                rating,
                alcohol_served,
                owner_id,
                brand_id,
                provenance,
                verification_status,
                created_at
            FROM restaurant_listings
            WHERE id = ?
            """.trimIndent(),
            { rs, _ -> rs.toListing() },
            id,
        )
        return rows.firstOrNull()
    }

    override fun updateVerificationStatus(id: UUID, status: VerificationStatus): RestaurantListing? {
        val updated: Int? = tx.execute {
            // Update the source row; no-op path returns 0 rows. The search mirror
            // is kept in sync in the SAME transaction so a promotion is atomically
            // visible to the public read surface (sc-73 → sc-49 verified display).
            val rows = jdbc.update(
                "UPDATE restaurant_listings SET verification_status = ? WHERE id = ?",
                status.name,
                id,
            )
            if (rows > 0) {
                jdbc.update(
                    "UPDATE listing_search SET verification_status = ? WHERE id = ?",
                    status.name,
                    id,
                )
            }
            rows
        }
        return if (updated != null && updated > 0) findById(id) else null
    }

    override fun findAll(): List<RestaurantListing> =
        jdbc.query(
            """
            SELECT
                id,
                name,
                address,
                ST_Y(location::geometry) AS lat,
                ST_X(location::geometry) AS lng,
                cuisine,
                is_hand_cut,
                price,
                rating,
                alcohol_served,
                owner_id,
                brand_id,
                provenance,
                verification_status,
                created_at
            FROM restaurant_listings
            ORDER BY created_at, name
            """.trimIndent(),
        ) { rs, _ -> rs.toListing() }

    private fun ResultSet.toListing(): RestaurantListing = RestaurantListing.fromStorage(
        id = getObject("id", UUID::class.java),
        name = getString("name"),
        address = getString("address"),
        location = LatLng(lat = getDouble("lat"), lng = getDouble("lng")),
        cuisine = getString("cuisine")?.let { Cuisine(it) },
        isHandCut = getObject("is_hand_cut", java.lang.Boolean::class.java) as Boolean?,
        price = getBigDecimal("price")?.let { Price(it) },
        rating = getBigDecimal("rating")?.let { Rating(it) },
        alcoholServed = getBoolean("alcohol_served"),
        ownerId = getObject("owner_id", UUID::class.java),
        brandId = getObject("brand_id", UUID::class.java),
        provenance = getString("provenance")?.let { Provenance(it) },
        verificationStatus = VerificationStatus.valueOf(getString("verification_status")),
        createdAt = getTimestamp("created_at").toInstant(),
    )
}

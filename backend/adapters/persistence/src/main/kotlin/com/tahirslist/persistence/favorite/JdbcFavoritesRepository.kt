package com.tahirslist.persistence.favorite

import com.tahirslist.application.favorite.FavoritesRepository
import com.tahirslist.domain.restaurant.CrossContamination
import com.tahirslist.domain.restaurant.Cuisine
import com.tahirslist.domain.restaurant.HalalItem
import com.tahirslist.domain.restaurant.HalalScope
import com.tahirslist.domain.restaurant.LatLng
import com.tahirslist.domain.restaurant.Price
import com.tahirslist.domain.restaurant.Provenance
import com.tahirslist.domain.restaurant.Rating
import com.tahirslist.domain.restaurant.RestaurantListing
import com.tahirslist.domain.restaurant.VerificationStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.util.UUID

/**
 * JDBC implementation of the [FavoritesRepository] port against the
 * `favorites` table (see V11__create_favorites.sql). Spring JDBC only —
 * deliberately boring and explicit.
 *
 * [add] uses `ON CONFLICT DO NOTHING`: the composite PK (user_id, listing_id)
 * makes the idempotent POST contract structural, so a duplicate favourite is a
 * no-op rather than an error.
 *
 * [findFavoriteListings] joins favorites → restaurant_listings so the read
 * returns full listing rows (the browse-card shape) in one query — no N+1.
 */
@Repository
class JdbcFavoritesRepository(private val jdbc: JdbcTemplate) : FavoritesRepository {

    override fun add(userId: UUID, listingId: UUID) {
        jdbc.update(
            """
            INSERT INTO favorites (user_id, listing_id)
            VALUES (?, ?)
            ON CONFLICT (user_id, listing_id) DO NOTHING
            """.trimIndent(),
            userId, listingId,
        )
    }

    override fun remove(userId: UUID, listingId: UUID) {
        jdbc.update(
            "DELETE FROM favorites WHERE user_id = ? AND listing_id = ?",
            userId, listingId,
        )
    }

    override fun findFavoriteListings(userId: UUID): List<RestaurantListing> =
        jdbc.query(
            """
            SELECT
                l.id,
                l.name,
                l.address,
                ST_Y(l.location::geometry) AS lat,
                ST_X(l.location::geometry) AS lng,
                l.cuisine,
                l.is_hand_cut,
                l.price,
                l.rating,
                l.alcohol_served,
                l.halal_scope,
                l.cross_contamination,
                l.owner_id,
                l.brand_id,
                l.provenance,
                l.verification_status,
                l.created_at
            FROM favorites f
            JOIN restaurant_listings l ON l.id = f.listing_id
            WHERE f.user_id = ?
            ORDER BY f.created_at DESC, l.name
            """.trimIndent(),
            { rs, _ -> rs.toListing() },
            userId,
        )

    private fun ResultSet.toListing(): RestaurantListing = RestaurantListing.fromStorage(
        id = getObject("id", UUID::class.java),
        name = getString("name"),
        address = getString("address"),
        location = LatLng(lat = getDouble("lat"), lng = getDouble("lng")),
        cuisine = getString("cuisine")?.let { Cuisine(it) },
        isHandCut = getObject("is_hand_cut", java.lang.Boolean::class.java) as Boolean?,
        price = getBigDecimal("price")?.let { Price(it) },
        rating = getBigDecimal("rating")?.let { Rating(it) },
        halalScope = HalalScope.valueOf(getString("halal_scope")),
        crossContamination = CrossContamination.valueOf(getString("cross_contamination")),
        ownerId = getObject("owner_id", UUID::class.java),
        brandId = getObject("brand_id", UUID::class.java),
        provenance = getString("provenance")?.let { Provenance(it) },
        verificationStatus = VerificationStatus.valueOf(getString("verification_status")),
        createdAt = getTimestamp("created_at").toInstant(),
    ).withHalalItems()

    /** Load the per-item halal scope child rows (sc-119). */
    private fun RestaurantListing.withHalalItems(): RestaurantListing {
        val items = jdbc.query(
            """
            SELECT name, is_halal
            FROM restaurant_halal_items
            WHERE listing_id = ?
            ORDER BY name
            """.trimIndent(),
            { rs, _ -> HalalItem(name = rs.getString("name"), isHalal = rs.getBoolean("is_halal")) },
            id,
        )
        return if (items.isEmpty()) this else copy(halalItems = items.toSet())
    }
}
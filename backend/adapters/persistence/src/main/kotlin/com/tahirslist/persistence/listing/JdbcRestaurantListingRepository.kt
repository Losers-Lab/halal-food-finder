package com.tahirslist.persistence.listing

import com.tahirslist.application.listing.RestaurantListingRepository
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
import org.springframework.transaction.support.TransactionTemplate
import java.sql.ResultSet
import java.util.UUID

/**
 * JDBC implementation of the [RestaurantListingRepository] port against the
 * `restaurant_listings` table (see V4__create_restaurant_listings.sql and
 * V18__sc_119_partial_halal.sql). Spring JDBC only — deliberately boring and
 * explicit. The database owns id generation.
 *
 * A location point is written with `ST_SetSRID(ST_MakePoint(lng, lat), 4326)`
 * (ST_MakePoint takes x=longitude first) and cast to the `geography(Point,4326)`
 * column; it is read back by casting the geography to geometry so ST_X/ST_Y
 * yield longitude/latitude.
 *
 * [save] writes the denormalised `listing_search` projection in the SAME
 * transaction, so a newly-added listing is immediately searchable (sc-10) and
 * the two tables can never diverge on this write path.
 *
 * sc-119: the per-item halal scope is stored in the `restaurant_halal_items`
 * child table, and [mirrorIntoListingSearch] is the cross-contamination INDEX
 * GATE — only [CrossContamination.isIndexQualified] listings are present in
 * `listing_search`; non-qualified rows are removed from the mirror so the index
 * never holds PRESENT/UNCERTAIN listings.
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
                INSERT INTO restaurant_listings (name, address, location, cuisine, is_hand_cut, is_delivery, owner_id, brand_id, provenance, verification_status, price, rating, alcohol_served, halal_scope, cross_contamination)
                VALUES (
                    ?, ?,
                    ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography,
                    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
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
                listing.isDelivery,
                listing.ownerId,
                listing.brandId,
                listing.provenance?.value,
                listing.verificationStatus.name,
                listing.price?.value,
                listing.rating?.value,
                listing.alcoholServed,
                listing.halalScope.name,
                listing.crossContamination.name,
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
            // Per-item halal scope (sc-119), mirroring the multi-cuisine shape.
            replaceHalalItems(id, withId.halalItems)
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
     *
     * sc-119 cross-contamination INDEX GATE: a listing is present in the mirror
     * ONLY when [CrossContamination.NO_CROSS_CONTAMINATION]. PRESENT/UNCERTAIN
     * listings are deleted from the index (removed if previously present).
     */
    private fun mirrorIntoListingSearch(listing: RestaurantListing) {
        if (listing.crossContamination.isIndexQualified()) {
            jdbc.update(
                """
                INSERT INTO listing_search (id, name, address, location, cuisine, is_hand_cut, is_delivery, verification_status, price, rating, alcohol_served, halal_scope, cross_contamination)
                VALUES (?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    name = EXCLUDED.name,
                    address = EXCLUDED.address,
                    location = EXCLUDED.location,
                    cuisine = EXCLUDED.cuisine,
                    is_hand_cut = EXCLUDED.is_hand_cut,
                    is_delivery = EXCLUDED.is_delivery,
                    verification_status = EXCLUDED.verification_status,
                    price = EXCLUDED.price,
                    rating = EXCLUDED.rating,
                    alcohol_served = EXCLUDED.alcohol_served,
                    halal_scope = EXCLUDED.halal_scope,
                    cross_contamination = EXCLUDED.cross_contamination
                """.trimIndent(),
                listing.id,
                listing.name,
                listing.address,
                listing.location.lng,
                listing.location.lat,
                listing.cuisine?.value,
                listing.isHandCut,
                listing.isDelivery,
                listing.verificationStatus.name,
                listing.price?.value,
                listing.rating?.value,
                listing.alcoholServed,
                listing.halalScope.name,
                listing.crossContamination.name,
            )
        } else {
            // Index gate: remove non-qualified listings from the search index.
            jdbc.update("DELETE FROM listing_search WHERE id = ?", listing.id)
        }
    }

    private fun replaceHalalItems(listingId: UUID, items: Set<HalalItem>) {
        jdbc.update("DELETE FROM restaurant_halal_items WHERE listing_id = ?", listingId)
        items.forEach {
            jdbc.update(
                "INSERT INTO restaurant_halal_items (listing_id, name, is_halal) VALUES (?, ?, ?)",
                listingId, it.name, it.isHalal,
            )
        }
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
                is_delivery,
                price,
                rating,
                alcohol_served,
                halal_scope,
                cross_contamination,
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
                is_delivery,
                price,
                rating,
                alcohol_served,
                halal_scope,
                cross_contamination,
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
        isDelivery = getObject("is_delivery", java.lang.Boolean::class.java) as Boolean?,
        price = getBigDecimal("price")?.let { Price(it) },
        rating = getBigDecimal("rating")?.let { Rating(it) },
        alcoholServed = getBoolean("alcohol_served"),
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
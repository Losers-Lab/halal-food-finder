package app.halal.persistence.listing

import app.halal.application.listing.RestaurantListingRepository
import app.halal.domain.restaurant.Cuisine
import app.halal.domain.restaurant.CuttingMethod
import app.halal.domain.restaurant.LatLng
import app.halal.domain.restaurant.RestaurantListing
import app.halal.domain.restaurant.VerificationStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
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
 */
@Repository
class JdbcRestaurantListingRepository(private val jdbc: JdbcTemplate) : RestaurantListingRepository {

    override fun save(listing: RestaurantListing): RestaurantListing {
        val id = jdbc.queryForObject(
            """
            INSERT INTO restaurant_listings (name, address, location, cuisine, cutting_method, owner_id, verification_status)
            VALUES (
                ?, ?,
                ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography,
                ?, ?, ?, ?
            )
            RETURNING id
            """.trimIndent(),
            UUID::class.java,
            listing.name,
            listing.address,
            listing.location.lng, // ST_MakePoint(x = longitude, y = latitude)
            listing.location.lat,
            listing.cuisine.value,
            listing.cuttingMethod.name,
            listing.ownerId,
            listing.verificationStatus.name,
        ) ?: error("INSERT RETURNING id returned no row")

        return listing.copy(id = id)
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
                cutting_method,
                owner_id,
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

    private fun ResultSet.toListing(): RestaurantListing = RestaurantListing.fromStorage(
        id = getObject("id", UUID::class.java),
        name = getString("name"),
        address = getString("address"),
        location = LatLng(lat = getDouble("lat"), lng = getDouble("lng")),
        cuisine = Cuisine(getString("cuisine")),
        cuttingMethod = CuttingMethod.valueOf(getString("cutting_method")),
        ownerId = getObject("owner_id", UUID::class.java),
        verificationStatus = VerificationStatus.valueOf(getString("verification_status")),
        createdAt = getTimestamp("created_at").toInstant(),
    )
}

package app.halal.domain.restaurant

/**
 * A geographic point (WGS84), matching the `geography(Point,4326)` column that
 * stores a listing's location. [lat] is latitude in `[-90, 90]`, [lng] is
 * longitude in `[-180, 180]`.
 */
data class LatLng(
    val lat: Double,
    val lng: Double,
) {
    init {
        require(lat in -90.0..90.0) { "Latitude must be within [-90, 90]. Got: $lat" }
        require(lng in -180.0..180.0) { "Longitude must be within [-180, 180]. Got: $lng" }
    }
}

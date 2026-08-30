package app.halal.application.geo

/**
 * The geocoding seam (hexagonal "out" port) shared by every provider adapter.
 *
 * Adapters impl this port so the premium provider can be swapped by config, not
 * code (docs/reviews/sc-138-external-services.md §3): Photon ($0, default),
 * Google Geocoding (premium), OSM Nominatim (no-card fallback — never client
 * autocomplete). All calls are held server-side: no client sees a key.
 *
 * Contract:
 *  - `geocode` returns null when the provider finds no match.
 *  - provider failures (non-2xx, timeout, unparseable response) throw
 *    [GeocodingException]; the caller treats those as external-service
 *    unavailability, not "no match".
 *
 * The **autocomplete** and **reverse-geocode** variants are deliberately not part
 * of this port yet — those are open/future UX items flagged in the gate (§5), and
 * adding them here before a caller needs them would over-build the seam.
 */
interface GeocoderPort {
    fun geocode(address: String): GeocodedPlace?
}

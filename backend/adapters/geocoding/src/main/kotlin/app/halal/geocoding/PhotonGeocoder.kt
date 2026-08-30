package app.halal.geocoding

import app.halal.application.geo.GeocodedPlace
import app.halal.application.geo.GeocodingException
import app.halal.application.geo.GeocoderPort
import app.halal.domain.restaurant.LatLng
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

/**
 * [GeocoderPort] adapter for **Photon** (photon.komoot.io) — the $0, no-key,
 * OSM-backed default geocoder (docs/reviews/sc-138-external-services.md §2.1).
 *
 * Server-side only; the JDK HttpClient means no new production dependency. The
 * adapter is cheap to configure (constructor-injected [baseUrl] + [timeout]) so
 * swapping to the premium Google adapter later is config, not code — see the
 * GeocoderPort KDoc for the swap contract. The public service is best-effort
 * (no SLA); production self-hosting Photon is a later scaling decision, not this
 * task. OSM data is ODbL — attribution/share-alike on derived fields is an open
 * founder decision, flagged not decided here.
 */
class PhotonGeocoder(
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
    private val baseUrl: String = "https://photon.komoot.io/api/",
    private val timeout: Duration = Duration.ofSeconds(5),
) : GeocoderPort {

    private val objectMapper = ObjectMapper()

    override fun geocode(address: String): GeocodedPlace? {
        val encoded = URLEncoder.encode(address, StandardCharsets.UTF_8)
        val uri = URI.create("${baseUrl.trimEnd('/')}/?q=$encoded")
        val request = HttpRequest.newBuilder(uri)
            .timeout(timeout)
            .GET()
            .build()

        val response = try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (e: IOException) {
            // Covers connect failures, response timeouts (HttpTimeoutException is
            // an IOException), and body-read failures.
            throw GeocodingException("Photon geocoder request failed for address: $address", e)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw GeocodingException("Photon geocoder request interrupted for address: $address", e)
        }

        if (response.statusCode() != 200) {
            throw GeocodingException("Photon geocoder returned HTTP ${response.statusCode()} for address: $address")
        }

        return parse(response.body().orEmpty(), address)
    }

    /** Maps Photon's GeoJSON FeatureCollection to a [GeocodedPlace] (or null for no match). */
    private fun parse(body: String, address: String): GeocodedPlace? {
        // readTree("") yields a MissingNode rather than throwing, so treat an empty
        // body as a provider failure explicitly instead of a silent "no match".
        if (body.isBlank()) {
            throw GeocodingException("Photon geocoder returned an empty response for address: $address")
        }

        val root = try {
            objectMapper.readTree(body)
        } catch (e: IOException) {
            throw GeocodingException("Photon geocoder returned an unparseable response for address: $address", e)
        }

        val features = root.get("features")
        if (features == null || !features.isArray || features.size() == 0) return null

        val coordinates = features.first().get("geometry")?.get("coordinates")
        if (coordinates == null || !coordinates.isArray || coordinates.size() < 2) return null

        // GeoJSON coordinates are [longitude, latitude].
        val lng = coordinates.get(0).asDouble()
        val lat = coordinates.get(1).asDouble()
        val location = try {
            LatLng(lat = lat, lng = lng)
        } catch (e: IllegalArgumentException) {
            throw GeocodingException("Photon geocoder returned invalid coordinates [$lat, $lng] for address: $address", e)
        }

        val props = features.first().get("properties")
        return GeocodedPlace(
            location = location,
            displayName = displayName(props),
            providerRef = providerRef(props),
        )
    }

    /** Best-effort human label: the OSM name, else a "street, city" fallback. */
    private fun displayName(props: JsonNode?): String {
        val name = text(props, "name")
        if (!name.isNullOrBlank()) return name
        return listOfNotNull(text(props, "street"), text(props, "city"))
            .filter { it.isNotBlank() }
            .joinToString(", ")
    }

    /** A stable provider reference (e.g. "N/123456" for OSM node 123456) if present. */
    private fun providerRef(props: JsonNode?): String? {
        val osmType = text(props, "osm_type")
        val osmId = text(props, "osm_id")
        return if (!osmType.isNullOrBlank() && !osmId.isNullOrBlank()) "$osmType/$osmId" else null
    }

    private fun text(props: JsonNode?, field: String): String? =
        props?.get(field)?.takeUnless { it.isNull }?.asText()
}

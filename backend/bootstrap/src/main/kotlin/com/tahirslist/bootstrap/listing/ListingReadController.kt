package com.tahirslist.bootstrap.listing

import com.tahirslist.application.image.ImagePort
import com.tahirslist.application.image.ImageVariant
import com.tahirslist.application.image.StoredImage
import com.tahirslist.application.listing.ListingSearchQuery
import com.tahirslist.application.listing.ListingSearchResult
import com.tahirslist.application.listing.RestaurantListingRepository
import com.tahirslist.domain.restaurant.LatLng
import com.tahirslist.domain.restaurant.RestaurantListing
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Public read surface for sc-157 (docs/design/sc-157-image-variants.md):
 *
 *  - [browse] – GET /v1/listings  → minimal search/browse cards. Each card carries
 *    ONLY `imageThumbnailUrl` (the ≤400px variant), never the full-res object —
 *    "no oversized fetch on cards".
 *  - [search] – GET /v1/listings/search → listings within a radius of a centre,
 *    ordered by straight-line distance ascending (sc-10). Cards match browse
 *    (thumbnail-only) plus a `distanceMiles` field.
 *  - [detail] – GET /v1/listings/{id} → detail payload that carries `imageUrl`
 *    (full-res) for the hero.
 *  - [serve] – GET /v1/listings/{id}/image?variant=thumbnail|full → the variant
 *    bytes themselves (same-origin proxy so CSP `img-src 'self'` needs no change).
 *
 * These reads are public (search/browse is the core public UX). Writing/claiming
 * stays authenticated via the existing ListingController. Security-posture change
 * (new public GET routes) is flagged for Omar's review in the PR.
 */
@RestController
@RequestMapping("/v1/listings")
class ListingReadController(
    private val listings: RestaurantListingRepository,
    private val images: ImagePort,
    private val search: ListingSearchQuery,
) {

    @GetMapping("/search")
    @Operation(summary = "Search restaurants by location", description = "Returns listings within `radius` (miles) of `center` (latitude,longitude), ordered by straight-line distance ascending (sc-10). Public — the core search UX.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Search results (distance ascending)"),
            ApiResponse(responseCode = "400", description = "Malformed centre or radius"),
        ],
    )
    fun search(
        @RequestParam(value = "center", required = false) center: String?,
        @RequestParam(value = "radius", required = false) radius: Double?,
        @RequestParam(value = "offset", defaultValue = "0") offset: Int,
        @RequestParam(value = "limit", defaultValue = "50") limit: Int,
    ): List<SearchCard> {
        // Missing / malformed centre or radius -> IllegalArgumentException (-> 400),
        // never a 500. Deferring to the global handler keeps the public search
        // surface gracefully defensive (sc-10 acceptance: no crash on bad input).
        val parsed = parseCenter(center)
        requireNotNull(radius) { "radius is required" }
        require(radius > 0.0) { "radius must be positive" }
        return search.searchNearby(
            center = parsed,
            radiusMiles = radius,
            offset = offset.coerceAtLeast(0),
            limit = limit.coerceIn(1, 100),
        ).map(::toSearchCard)
    }

    @GetMapping
    @Operation(summary = "Browse restaurants", description = "Minimal unfiltered browse surface (sc-157). Each card exposes only a thumbnail-size hero. Full filtered search is a later story.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Browse cards"),
        ],
    )
    fun browse(): List<BrowseCard> =
        listings.findAll().map(::toBrowseCard)

    @GetMapping("/{id}")
    @Operation(summary = "Restaurant detail", description = "Full listing detail including the full-res hero image URL.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Detail payload"),
            ApiResponse(responseCode = "404", description = "Listing not found"),
        ],
    )
    fun detail(@PathVariable id: UUID): ResponseEntity<DetailResponse> {
        val listing = listings.findById(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(toDetail(listing))
    }

    @GetMapping("/{id}/image")
    @Operation(summary = "Serve an image variant", description = "Same-origin proxy returning only the requested variant's bytes (thumbnail ≤400px, or full original).")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Image bytes"),
            ApiResponse(responseCode = "400", description = "Unknown variant"),
            ApiResponse(responseCode = "404", description = "No stored image for this variant"),
        ],
    )
    fun serve(
        @PathVariable id: UUID,
        @RequestParam("variant") variant: String,
    ): ResponseEntity<ByteArray> {
        val parsed = parseVariant(variant)
            ?: return ResponseEntity.badRequest().build()
        val stored: StoredImage = images.load(id, parsed)
            ?: return ResponseEntity.notFound().build()
        val mediaType = runCatching { MediaType.parseMediaType(stored.contentType) }
            .getOrDefault(MediaType.APPLICATION_OCTET_STREAM)
        return ResponseEntity.ok()
            .contentType(mediaType)
            .cacheControl(CacheControl.maxAge(86_400, TimeUnit.SECONDS).cachePublic())
            .body(stored.bytes)
    }

    private fun parseVariant(raw: String): ImageVariant? = when (raw.lowercase()) {
        "thumbnail" -> ImageVariant.THUMBNAIL
        "full" -> ImageVariant.FULL
        else -> null
    }

    /** Parses `center=<lat>,<lng>`. Missing / malformed → IllegalArgumentException (→ 400). */
    private fun parseCenter(raw: String?): LatLng {
        requireNotNull(raw) { "center is required" }
        val parts = raw.split(",")
        require(parts.size == 2) { "center must be '<lat>,<lng>'" }
        val lat = parts[0].trim().toDoubleOrNull() ?: throw IllegalArgumentException("center lat invalid")
        val lng = parts[1].trim().toDoubleOrNull() ?: throw IllegalArgumentException("center lng invalid")
        return LatLng(lat = lat, lng = lng) // LatLng validates the [-90,90]/[-180,180] ranges
    }

    private fun toBrowseCard(listing: RestaurantListing): BrowseCard = BrowseCard(
        id = listing.id,
        name = listing.name,
        address = listing.address,
        lat = listing.location.lat,
        lng = listing.location.lng,
        cuisine = listing.cuisine?.value,
        cuttingMethod = listing.cuttingMethod.name,
        verificationStatus = listing.verificationStatus.name,
        imageThumbnailUrl = imageUrl(listing.id, ImageVariant.THUMBNAIL),
    )

    private fun toSearchCard(result: ListingSearchResult): SearchCard = SearchCard(
        id = result.id,
        name = result.name,
        address = result.address,
        lat = result.location.lat,
        lng = result.location.lng,
        cuisine = result.cuisine?.value,
        cuttingMethod = result.cuttingMethod.name,
        verificationStatus = result.verificationStatus.name,
        imageThumbnailUrl = imageUrl(result.id, ImageVariant.THUMBNAIL),
        distanceMiles = result.distanceMiles,
    )

    private fun toDetail(listing: RestaurantListing): DetailResponse = DetailResponse(
        id = listing.id,
        name = listing.name,
        address = listing.address,
        lat = listing.location.lat,
        lng = listing.location.lng,
        cuisine = listing.cuisine?.value,
        cuttingMethod = listing.cuttingMethod.name,
        verificationStatus = listing.verificationStatus.name,
        imageThumbnailUrl = imageUrl(listing.id, ImageVariant.THUMBNAIL),
        imageUrl = imageUrl(listing.id, ImageVariant.FULL),
    )

    private fun imageUrl(id: UUID, variant: ImageVariant): String =
        ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/v1/listings/{id}/image")
            .queryParam("variant", variant.name.lowercase())
            .buildAndExpand(id)
            .toUriString()

    data class BrowseCard(
        val id: UUID,
        val name: String,
        val address: String,
        val lat: Double,
        val lng: Double,
        val cuisine: String?,
        val cuttingMethod: String,
        val verificationStatus: String,
        val imageThumbnailUrl: String,
    )

    data class SearchCard(
        val id: UUID,
        val name: String,
        val address: String,
        val lat: Double,
        val lng: Double,
        val cuisine: String?,
        val cuttingMethod: String,
        val verificationStatus: String,
        val imageThumbnailUrl: String,
        val distanceMiles: Double,
    )

    data class DetailResponse(
        val id: UUID,
        val name: String,
        val address: String,
        val lat: Double,
        val lng: Double,
        val cuisine: String?,
        val cuttingMethod: String,
        val verificationStatus: String,
        val imageThumbnailUrl: String,
        val imageUrl: String,
    )
}
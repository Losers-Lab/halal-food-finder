package com.tahirslist.bootstrap.listing

import com.tahirslist.application.image.ImagePort
import com.tahirslist.application.image.ImageVariant
import com.tahirslist.application.image.StoredImage
import com.tahirslist.application.listing.CuttingMethodFilter
import com.tahirslist.application.listing.CuisineLogic
import com.tahirslist.application.listing.ListingSearchFilters
import com.tahirslist.application.listing.ListingSearchQuery
import com.tahirslist.application.listing.ListingSearchResult
import com.tahirslist.application.listing.RestaurantListingRepository
import com.tahirslist.domain.restaurant.LatLng
import com.tahirslist.domain.restaurant.Price
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
import java.math.BigDecimal
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

    /** Upper bound of the closed 0..5 rating scale (sc-45), mirrored from the domain [Rating]. */
    private val ratingMax: Double = com.tahirslist.domain.restaurant.Rating.MAX.toDouble()

    @GetMapping("/search")
    @Operation(summary = "Search restaurants by location", description = "Returns listings within `radius` (miles) of `center` (latitude,longitude), ordered by straight-line distance ascending. `cuttingMethod` narrows to HAND_CUT/MACHINE_CUT (BOTH default = any); `cuisine` (repeatable) with `cuisineLogic` AND or OR (default OR) narrows by multi-cuisine membership; `minPrice`/`maxPrice` bound the price range; `minRating` sets the minimum listing rating on the 0..5 scale. (sc-10 location, sc-42 cutting, sc-43 price, sc-44 cuisine, sc-45 rating). Public — the core search UX.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Search results (distance ascending)"),
            ApiResponse(responseCode = "400", description = "Malformed centre, radius, cuttingMethod, cuisine, cuisineLogic, price, or rating"),
        ],
    )
    fun search(
        @RequestParam(value = "center", required = false) center: String?,
        @RequestParam(value = "radius", required = false) radius: Double?,
        @RequestParam(value = "cuttingMethod", required = false) cuttingMethod: String?,
        @RequestParam(value = "cuisine", required = false) cuisine: List<String>?,
        @RequestParam(value = "cuisineLogic", required = false) cuisineLogic: String?,
        @RequestParam(value = "minPrice", required = false) minPrice: Double?,
        @RequestParam(value = "maxPrice", required = false) maxPrice: Double?,
        @RequestParam(value = "minRating", required = false) minRating: Double?,
        @RequestParam(value = "offset", defaultValue = "0") offset: Int,
        @RequestParam(value = "limit", defaultValue = "50") limit: Int,
    ): List<SearchCard> {
        // Missing / malformed centre or radius -> IllegalArgumentException (-> 400),
        // never a 500. Deferring to the global handler keeps the public search
        // surface gracefully defensive (sc-10 acceptance: no crash on bad input).
        val parsed = parseCenter(center)
        requireNotNull(radius) { "radius is required" }
        require(radius > 0.0) { "radius must be positive" }
        // Unknown cuttingMethod / cuisineLogic / malformed price values all surface
        // as 400 invalid_input, never a 500 — the public search vocabulary is closed.
        val filters = ListingSearchFilters(
            cuttingMethod = parseCuttingMethod(cuttingMethod),
            cuisines = parseCuisines(cuisine),
            cuisineLogic = parseCuisineLogic(cuisineLogic),
            minPrice = parsePrice(minPrice, "minPrice"),
            maxPrice = parsePrice(maxPrice, "maxPrice"),
            minRating = parseRating(minRating),
        )
        val minBound = filters.minPrice
        val maxBound = filters.maxPrice
        require(minBound == null || maxBound == null || minBound <= maxBound) { "minPrice must not exceed maxPrice" }
        return search.searchNearby(
            center = parsed,
            radiusMiles = radius,
            filters = filters,
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

    /**
     * Parses the `cuttingMethod` filter (sc-42). Missing / blank / `BOTH` mean
     * "any" (no narrowing); HAND_CUT and MACHINE_CUT narrow the search. Any other
     * value → IllegalArgumentException (→ 400 invalid_input, never a 500) so the
     * public contract HAND_CUT|MACHINE_CUT|BOTH is the only accepted vocabulary.
     */
    private fun parseCuttingMethod(raw: String?): CuttingMethodFilter {
        val value = raw?.trim()?.uppercase()
        return when (value) {
            null, "", "BOTH" -> CuttingMethodFilter.BOTH
            "HAND_CUT" -> CuttingMethodFilter.HAND_CUT
            "MACHINE_CUT" -> CuttingMethodFilter.MACHINE_CUT
            else -> throw IllegalArgumentException("cuttingMethod must be HAND_CUT|MACHINE_CUT|BOTH")
        }
    }

    /**
     * Parses the `cuisine` filters (sc-44). Values are trimmed + lowercased to
     * match the lowercase-stored values and de-duplicated. A blank or over-long
     * value (the [Cuisine] boundary) → IllegalArgumentException (→ 400). Empty
     * / absent means "any cuisine" (no narrowing).
     */
    private fun parseCuisines(raw: List<String>?): List<String> {
        if (raw.isNullOrEmpty()) return emptyList()
        return raw.map {
            val value = it.trim().lowercase()
            require(value.isNotBlank()) { "cuisine must not be blank" }
            require(value.length <= 64) { "cuisine must be 64 characters or fewer" }
            value
        }.distinct()
    }

    /**
     * Parses the `cuisineLogic` parameter (sc-44). Missing / blank / `OR` is the
     * PRD default; `AND` requires all selected cuisines. Any other value →
     * IllegalArgumentException (→ 400), keeping the public vocabulary closed.
     */
    private fun parseCuisineLogic(raw: String?): CuisineLogic = when (raw?.trim()?.uppercase()) {
        null, "", "OR" -> CuisineLogic.OR
        "AND" -> CuisineLogic.AND
        else -> throw IllegalArgumentException("cuisineLogic must be AND|OR")
    }

    /**
     * Parses a price-bound filter (sc-43). Absent → null (unbounded). Negative
     * or above the domain [Price] ceiling → IllegalArgumentException (→ 400).
     */
    private fun parsePrice(raw: Double?, name: String): BigDecimal? {
        if (raw == null) return null
        require(raw >= 0.0) { "$name must not be negative" }
        val value = BigDecimal.valueOf(raw)
        require(value <= Price.MAX) { "$name must be ${Price.MAX} or fewer" }
        return value
    }

    /**
     * Parses the `minRating` filter (sc-45). Absent -> null (any rating).
     * Out-of-scale (below 0 or above 5) -> IllegalArgumentException (-> 400),
     * keeping the public contract the closed 0..5 scale.
     */
    private fun parseRating(raw: Double?): BigDecimal? {
        if (raw == null) return null
        require(raw >= 0.0) { "minRating must not be negative" }
        require(raw <= ratingMax) { "minRating must be 5.0 or fewer" }
        return BigDecimal.valueOf(raw)
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
        rating = result.rating?.value?.toDouble(),
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
        val rating: Double?,
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
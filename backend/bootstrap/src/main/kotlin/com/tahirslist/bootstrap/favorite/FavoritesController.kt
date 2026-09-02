package com.tahirslist.bootstrap.favorite

import com.tahirslist.application.favorite.FavoriteListing
import com.tahirslist.application.favorite.ListFavorites
import com.tahirslist.application.favorite.ListingNotFoundException
import com.tahirslist.application.favorite.UnfavoriteListing
import com.tahirslist.application.image.ImageVariant
import com.tahirslist.bootstrap.listing.ListingReadController
import com.tahirslist.domain.restaurant.RestaurantListing
import com.tahirslist.web.account.ErrorResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import java.util.UUID

/**
 * Favourites surface (sc-50/51/52): POST /v1/favorites/{listingId},
 * DELETE /v1/favorites/{listingId}, and GET /v1/favorites.
 *
 * Every request requires a valid access JWT and acts on the **authenticated
 * account** (the JWT `sub`), never a client-supplied id — Webb's favorite is
 * Webb's alone. POST and DELETE are idempotent (204), GET returns the user's
 * favourites as [ListingReadController.BrowseCard] objects — the exact
 * `/v1/listings` read shape — so the frontend favourites page can reuse
 * ListingCard/ListingGrid with no per-row N+1.
 *
 * Authentication is enforced by the deny-by-default resource server (sc-131):
 * these routes are not permitted publicly, so a missing/expired/tampered JWT is
 * rejected with a generic 401 before the handler is reached.
 */
@RestController
@RequestMapping("/v1/favorites")
class FavoritesController(
    private val favoriteListing: FavoriteListing,
    private val unfavoriteListing: UnfavoriteListing,
    private val listFavorites: ListFavorites,
) {

    @PostMapping("/{listingId}")
    @Operation(summary = "Favourite a listing", description = "Records the authenticated user's favourite for a listing. Idempotent — favouriting the same listing twice is a no-op.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Favourited (no body)"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "404", description = "Listing not found"),
        ],
    )
    fun favorite(
        @PathVariable listingId: UUID,
        authentication: JwtAuthenticationToken,
    ): ResponseEntity<Void> {
        val userId = UUID.fromString(authentication.token.subject)
        favoriteListing.execute(userId, listingId)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/{listingId}")
    @Operation(summary = "Unfavourite a listing", description = "Removes the authenticated user's favourite for a listing. Idempotent — unfavouriting a listing that is not favourited is a no-op.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Unfavourited (no body)"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
        ],
    )
    fun unfavorite(
        @PathVariable listingId: UUID,
        authentication: JwtAuthenticationToken,
    ): ResponseEntity<Void> {
        val userId = UUID.fromString(authentication.token.subject)
        unfavoriteListing.execute(userId, listingId)
        return ResponseEntity.noContent().build()
    }

    @GetMapping
    @Operation(summary = "List the authenticated user's favourites", description = "Returns the authenticated user's favourited listings as browse-card objects identical to the /v1/listings read shape.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "The user's favourite browse cards", content = [Content(schema = Schema(implementation = ListingReadController.BrowseCard::class))]),
            ApiResponse(responseCode = "401", description = "Authentication required"),
        ],
    )
    fun list(authentication: JwtAuthenticationToken): List<ListingReadController.BrowseCard> {
        val userId = UUID.fromString(authentication.token.subject)
        return listFavorites.execute(userId).map(::toBrowseCard)
    }

    @ExceptionHandler(ListingNotFoundException::class)
    @ApiResponse(responseCode = "404", description = "Listing not found")
    fun onListingNotFound(): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse("listing_not_found"))

    private fun toBrowseCard(listing: RestaurantListing): ListingReadController.BrowseCard =
        ListingReadController.BrowseCard(
            id = listing.id,
            name = listing.name,
            address = listing.address,
            lat = listing.location.lat,
            lng = listing.location.lng,
            cuisine = listing.cuisine?.value,
            isHandCut = listing.isHandCut,
            halalScope = listing.halalScope.name,
            verificationStatus = listing.verificationStatus.name,
            imageThumbnailUrl = imageUrl(listing.id, ImageVariant.THUMBNAIL_400),
            imageSrcset = imageSrcset(listing.id),
        )

    private fun imageUrl(id: UUID, variant: ImageVariant): String =
        ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/v1/listings/{id}/image")
            .queryParam("variant", variantSlug(variant))
            .buildAndExpand(id)
            .toUriString()

    /** The sc-183 responsive width set a frontend may turn into a `srcset`. */
    private fun imageSrcset(id: UUID): List<ListingReadController.SrcsetEntry> =
        ImageVariant.thumbnailVariants.map { variant ->
            ListingReadController.SrcsetEntry(width = variant.widthPx!!, url = imageUrl(id, variant))
        }

    private fun variantSlug(variant: ImageVariant): String = when (variant) {
        ImageVariant.THUMBNAIL_400 -> "thumbnail"
        ImageVariant.THUMBNAIL_768 -> "thumbnail_768"
        ImageVariant.THUMBNAIL_1280 -> "thumbnail_1280"
        ImageVariant.THUMBNAIL_1920 -> "thumbnail_1920"
        ImageVariant.FULL -> "full"
    }
}
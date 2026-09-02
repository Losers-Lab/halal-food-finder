package com.tahirslist.bootstrap.listing

import com.tahirslist.application.listing.CreateListing
import com.tahirslist.application.listing.ListingOwnerNotFoundException
import com.tahirslist.domain.restaurant.Cuisine
import com.tahirslist.domain.restaurant.LatLng
import com.tahirslist.domain.restaurant.RestaurantListing
import com.tahirslist.domain.restaurant.VerificationStatus
import com.tahirslist.web.account.ErrorResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

/**
 * Add Listing (sc-138) endpoint: POST /v1/listings.
 *
 * Requires a valid access JWT (deny-by-default resource server from sc-131 —
 * unauthenticated -> 401 before this reaches the handler). The listing's owner is
 * the **authenticated account** (the JWT `sub`), never a client-supplied owner.
 *
 * This endpoint does NOT geocode: per the sc-138 decision, geocoding stays out of
 * the listing write-path (docs/reviews/sc-138-external-services.md §3) so a
 * listing is always saveable independently of any external geocoding provider.
 * The client supplies coordinates directly; there is no outbound call on this
 * path to rate-limit.
 */
@RestController
@RequestMapping("/v1/listings")
class ListingController(private val createListing: CreateListing) {

    @PostMapping
    @Operation(summary = "Add a restaurant listing", description = "Creates a new, always-unverified restaurant listing owned by the authenticated account. Does not geocode; the client supplies coordinates.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "Listing created", content = [Content(schema = Schema(implementation = ListingResponse::class))]),
            ApiResponse(responseCode = "400", description = "Invalid input"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "404", description = "Owning account not found"),
        ],
    )
    fun create(
        @Valid @RequestBody request: CreateListingRequest,
        authentication: JwtAuthenticationToken,
    ): ResponseEntity<ListingResponse> {
        // The authenticated account is the listing owner (JWT sub = account id).
        val ownerId = UUID.fromString(authentication.token.subject)

        val listing = createListing.execute(
            name = request.name,
            address = request.address,
            location = LatLng(lat = request.lat!!, lng = request.lng!!),
            cuisine = Cuisine(request.cuisine),
            isHandCut = request.isHandCut,
            ownerId = ownerId,
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(ListingResponse.from(listing))
    }

    @ExceptionHandler(ListingOwnerNotFoundException::class)
    @ApiResponse(responseCode = "404", description = "Owning account not found")
    fun onOwnerNotFound(): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse("owner_not_found"))

    data class CreateListingRequest(
        @field:NotBlank(message = "name is required")
        val name: String,

        @field:NotBlank(message = "address is required")
        val address: String,

        @field:NotNull(message = "lat is required")
        val lat: Double?,

        @field:NotNull(message = "lng is required")
        val lng: Double?,

        @field:NotBlank(message = "cuisine is required")
        val cuisine: String,

        /**
         * Whether the listing is hand-cut (sc-42). Optional; null = unknown /
         * not claimed. There is no machine-cut concept — this is a plain boolean.
         */
        val isHandCut: Boolean?,
    )

    data class ListingResponse(
        val id: UUID,
        val name: String,
        val address: String,
        val lat: Double,
        val lng: Double,
        val cuisine: String,
        val isHandCut: Boolean?,
        val ownerId: UUID,
        val verificationStatus: VerificationStatus,
        val createdAt: Instant,
    ) {
        companion object {
            fun from(listing: RestaurantListing): ListingResponse = ListingResponse(
                id = listing.id,
                name = listing.name,
                address = listing.address,
                lat = listing.location.lat,
                lng = listing.location.lng,
                // The Add Listing flow always requires a cuisine, so the response
                // echoes a non-null value; the nullable domain field is only NULL
                // for community seed rows, which this endpoint does not create.
                cuisine = listing.cuisine!!.value,
                isHandCut = listing.isHandCut,
                // Add Listing always attaches the authenticated owner, so the
                // response echoes a non-null id; only community seed rows have
                // ownerId NULL, and this endpoint does not create those.
                ownerId = listing.ownerId!!,
                verificationStatus = listing.verificationStatus,
                createdAt = listing.createdAt,
            )
        }
    }
}
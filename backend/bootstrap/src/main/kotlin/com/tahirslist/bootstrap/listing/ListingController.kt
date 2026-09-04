package com.tahirslist.bootstrap.listing

import com.tahirslist.application.favorite.ListingNotFoundException
import com.tahirslist.application.image.StoredImage
import com.tahirslist.application.listing.AddListingImage
import com.tahirslist.application.listing.CreateListing
import com.tahirslist.application.listing.ListingOwnerNotFoundException
import com.tahirslist.application.listing.RemoveListingImage
import com.tahirslist.application.listing.UpdateListing
import com.tahirslist.application.verification.NotListingOwnerException
import com.tahirslist.domain.restaurant.CrossContamination
import com.tahirslist.domain.restaurant.Cuisine
import com.tahirslist.domain.restaurant.HalalItem
import com.tahirslist.domain.restaurant.HalalScope
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
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.multipart.support.MissingServletRequestPartException
import java.time.Instant
import java.util.UUID

/**
 * Add Listing (sc-138) endpoint: POST /v1/listings.
 * Owner listing edit (sc-23/47/48) endpoint: PATCH /v1/listings/{id}.
 * Owner image management (sc-53/54) endpoints: PUT/DELETE /v1/listings/{id}/image.
 *
 * All require a valid access JWT (deny-by-default resource server from sc-131 —
 * unauthenticated -> 401 before this reaches the handler). The listing's owner is
 * the **authenticated account** (the JWT `sub`), never a client-supplied owner.
 *
 * PATCH is a full replace of the editable content fields (same shape as the Add
 * Listing request, so the frontend reuses the add-listing form): the owner sends
 * the complete editable payload and identity/governance fields (owner, verification
 * status, price, rating) are preserved untouched. A non-owner -> 403, a missing
 * listing -> 404.
 *
 * PUT /{id}/image is a multipart upload that (re)places the listing's single hero
 * image (last-write-wins; sc-53). DELETE /{id}/image removes every stored variant
 * (sc-54); removal is idempotent (a listing with no image is not an error). Both
 * are owner-guarded; bytes live in the object store via [com.tahirslist.application.image.ImagePort],
 * never in the database.
 *
 * These endpoints do NOT geocode: per the sc-138 decision, geocoding stays out of
 * the listing write-path (docs/reviews/sc-138-external-services.md §3) so a listing
 * is always saveable independently of any external geocoding provider. The client
 * supplies coordinates directly; there is no outbound call on this path to
 * rate-limit.
 */
@RestController
@RequestMapping("/v1/listings")
class ListingController(
    private val createListing: CreateListing,
    private val updateListing: UpdateListing,
    private val addListingImage: AddListingImage,
    private val removeListingImage: RemoveListingImage,
) {

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
            city = request.city,
            province = request.province,
            postal = request.postal,
            country = request.country,
            location = LatLng(lat = request.lat!!, lng = request.lng!!),
            cuisine = Cuisine(request.cuisine),
            isHandCut = request.isHandCut,
            isDelivery = request.isDelivery,
            ownerId = ownerId,
            halalScope = request.halalScope,
            halalItems = request.halalItems,
            crossContamination = request.crossContamination,
            alcoholServed = request.alcoholServed,
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(ListingResponse.from(listing))
    }

    @ExceptionHandler(ListingOwnerNotFoundException::class)
    @ApiResponse(responseCode = "404", description = "Owning account not found")
    fun onOwnerNotFound(): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse("owner_not_found"))

    @ExceptionHandler(ListingNotFoundException::class)
    @ApiResponse(responseCode = "404", description = "Listing not found")
    fun onListingNotFound(): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse("listing_not_found"))

    @ExceptionHandler(NotListingOwnerException::class)
    @ApiResponse(responseCode = "403", description = "Only the listing owner may edit")
    fun onNotListingOwner(): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse("not_listing_owner"))

    @PatchMapping("/{id}")
    @Operation(summary = "Edit a restaurant listing", description = "Full replace of a listing's editable content fields by its owner (sc-23/47/48). Identity and governance fields (owner, verification status, price, rating) are preserved. Non-owner -> 403; missing listing -> 404.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Listing updated", content = [Content(schema = Schema(implementation = ListingResponse::class))]),
            ApiResponse(responseCode = "400", description = "Invalid input"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Only the listing owner may edit"),
            ApiResponse(responseCode = "404", description = "Listing not found"),
        ],
    )
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateListingRequest,
        authentication: JwtAuthenticationToken,
    ): ResponseEntity<ListingResponse> {
        val ownerId = UUID.fromString(authentication.token.subject)

        val listing = updateListing.execute(
            listingId = id,
            ownerId = ownerId,
            name = request.name,
            address = request.address,
            location = LatLng(lat = request.lat!!, lng = request.lng!!),
            cuisine = Cuisine(request.cuisine),
            isHandCut = request.isHandCut,
            isDelivery = request.isDelivery,
            halalScope = request.halalScope,
            halalItems = request.halalItems,
            crossContamination = request.crossContamination,
            alcoholServed = request.alcoholServed,
        )
        return ResponseEntity.ok(ListingResponse.from(listing))
    }

    /**
     * Owner add/replace listing image (sc-53): PUT /v1/listings/{id}/image.
     * Multipart upload of a single `image` part; last-write-wins replace of the
     * listing's one hero image (FULL + every sc-183 thumbnail width). Owner-only
     * (403 for others, 404 for unknown listings); an undecodable image -> 400.
     * Bytes go to the object store, not the database.
     */
    @PutMapping(value = ["/{id}/image"], consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(summary = "Add or replace a listing's hero image", description = "Owner uploads a single hero image (multipart `image` part). Last-write-wins replace: FULL + every thumbnail width are stored (sc-53). Non-owner -> 403; missing listing -> 404; undecodable image -> 400.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Image stored/replaced"),
            ApiResponse(responseCode = "400", description = "No image part, or image is not a decodable image"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Only the listing owner may edit"),
            ApiResponse(responseCode = "404", description = "Listing not found"),
        ],
    )
    fun addImage(
        @PathVariable id: UUID,
        @RequestPart("image") image: MultipartFile,
        authentication: JwtAuthenticationToken,
    ): ResponseEntity<Void> {
        addListingImage.execute(
            listingId = id,
            ownerId = UUID.fromString(authentication.token.subject),
            original = StoredImage(
                bytes = image.bytes,
                contentType = image.contentType ?: "application/octet-stream",
            ),
        )
        return ResponseEntity.noContent().build()
    }

    @ExceptionHandler(MissingServletRequestPartException::class)
    @ApiResponse(responseCode = "400", description = "Multipart request is missing the `image` part")
    fun onMissingPart(): ResponseEntity<ErrorResponse> =
        ResponseEntity.badRequest().body(ErrorResponse("invalid_input", "Image part is required."))

    /**
     * Owner remove listing image (sc-54): DELETE /v1/listings/{id}/image.
     * Removes every stored variant (FULL + thumbnails). Idempotent: a listing with
     * no image is not an error (204 either way). Owner-only (403 for others,
     * 404 for unknown listings).
     */
    @DeleteMapping("/{id}/image")
    @Operation(summary = "Remove a listing's hero image", description = "Owner removes every stored variant of the listing's hero image (sc-54). Idempotent: removing an image the listing never had is not an error. Non-owner -> 403; missing listing -> 404.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Image removed (or had nothing to remove)"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Only the listing owner may edit"),
            ApiResponse(responseCode = "404", description = "Listing not found"),
        ],
    )
    fun removeImage(
        @PathVariable id: UUID,
        authentication: JwtAuthenticationToken,
    ): ResponseEntity<Void> {
        removeListingImage.execute(
            listingId = id,
            ownerId = UUID.fromString(authentication.token.subject),
        )
        return ResponseEntity.noContent().build()
    }

    data class CreateListingRequest(
        @field:NotBlank(message = "name is required")
        val name: String,

        @field:NotBlank(message = "address is required")
        val address: String,

        /** Structured address (sc-187): city/province/postal/country, optional. null = unknown. */
        val city: String? = null,
        val province: String? = null,
        val postal: String? = null,
        val country: String? = null,

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

        val halalScope: HalalScope = HalalScope.DEFAULT,

        val halalItems: Set<HalalItem> = emptySet(),

        val crossContamination: CrossContamination = CrossContamination.DEFAULT,

        val alcoholServed: Boolean = false,

        /**
         * Whether the listing offers delivery (sc-184). Optional; null = unknown /
         * not claimed. Modelled on the sc-42 pattern: pickup is the implicit
         * baseline default, delivery is the extra flag a listing claims.
         */
        val isDelivery: Boolean?,
    )

    /**
     * Edit Listing request (sc-23/47/48). Mirrors [CreateListingRequest] so the
     * frontend reuses the add-listing form. This is a FULL replace of the editable
     * content fields: the owner submits the complete editable payload and identity/
     * governance fields (owner, verification status, price, rating) are preserved
     * untouched server-side. As with create, omitting a nullable boolean
     * (isHandCut / isDelivery) clears it to null (unknown).
     */
    data class UpdateListingRequest(
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

        val isHandCut: Boolean?,

        val halalScope: HalalScope = HalalScope.DEFAULT,

        val halalItems: Set<HalalItem> = emptySet(),

        val crossContamination: CrossContamination = CrossContamination.DEFAULT,

        val alcoholServed: Boolean = false,

        val isDelivery: Boolean?,
    )

    data class ListingResponse(
        val id: UUID,
        val name: String,
        val address: String,
        val city: String?,
        val province: String?,
        val postal: String?,
        val country: String?,
        val lat: Double,
        val lng: Double,
        val cuisine: String,
        val isHandCut: Boolean?,
        val halalScope: HalalScope,
        val isDelivery: Boolean?,
        val ownerId: UUID,
        val verificationStatus: VerificationStatus,
        val createdAt: Instant,
    ) {
        companion object {
            fun from(listing: RestaurantListing): ListingResponse = ListingResponse(
                id = listing.id,
                name = listing.name,
                address = listing.address,
                city = listing.city,
                province = listing.province,
                postal = listing.postal,
                country = listing.country,
                lat = listing.location.lat,
                lng = listing.location.lng,
                // The Add Listing flow always requires a cuisine, so the response
                // echoes a non-null value; the nullable domain field is only NULL
                // for community seed rows, which this endpoint does not create.
                cuisine = listing.cuisine!!.value,
                isHandCut = listing.isHandCut,
                halalScope = listing.halalScope,
                isDelivery = listing.isDelivery,
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
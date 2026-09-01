package com.tahirslist.bootstrap.verification

import com.tahirslist.application.favorite.ListingNotFoundException
import com.tahirslist.application.verification.ClaimListing
import com.tahirslist.application.verification.NotListingOwnerException
import com.tahirslist.application.verification.VerificationUnavailableException
import com.tahirslist.domain.verification.HalalCertificationReview
import com.tahirslist.domain.verification.VerificationState
import com.tahirslist.web.account.ErrorResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

/**
 * Owner claim endpoint (sc-46): POST /v1/listings/{listingId}/claim
 *
 * An authenticated account submits proof of ownership (text) plus a photo of its
 * halal certification (image), and the listing is driven through the sc-117
 * verification state machine. Two guards:
 *
 *  1. The deny-by-default resource server (sc-131) requires a valid access JWT —
 *     unauthenticated -> generic 401 before this handler runs.
 *  2. The owner-scoped guard in [ClaimListing]: only the listed `owner_id` may
 *     claim (non-owner -> 403, missing listing -> 404). The acting account is
 *     always the JWT `sub`, never a client-supplied owner.
 *
 * The AI can only ever *suggest*; a review here never reaches APPROVED on its
 * own — the Verification Committee decides that in sc-73.
 */
@RestController
@RequestMapping("/v1/listings")
class VerificationClaimController(private val claimListing: ClaimListing) {

    @PostMapping("/{listingId}/claim")
    @Operation(summary = "Owner-claim verification", description = "Submits ownership proof + a certification image for the listing; drives the listing through the verification state machine (SUBMITTED -> AI_REVIEW -> AI_SUGGESTED) and returns the created review.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "Review created and driven to AI_SUGGESTED", content = [Content(schema = Schema(implementation = ClaimResponse::class))]),
            ApiResponse(responseCode = "400", description = "Missing proof or certification image"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Only the listing owner may claim"),
            ApiResponse(responseCode = "404", description = "Listing not found"),
            ApiResponse(responseCode = "503", description = "Verification provider unavailable; review held for retry"),
        ],
    )
    fun claim(
        @PathVariable listingId: UUID,
        @RequestPart("proof") proof: String,
        @RequestPart("certImage") certImage: MultipartFile,
        authentication: JwtAuthenticationToken,
    ): ResponseEntity<ClaimResponse> {
        val claimerId = UUID.fromString(authentication.token.subject)
        val review = claimListing.execute(
            listingId = listingId,
            claimerId = claimerId,
            proof = proof,
            contentType = certImage.contentType ?: "application/octet-stream",
            imageBytes = certImage.bytes,
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(ClaimResponse.from(review))
    }

    @ExceptionHandler(ListingNotFoundException::class)
    @ApiResponse(responseCode = "404", description = "Listing not found")
    fun onListingNotFound(): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse("listing_not_found"))

    @ExceptionHandler(NotListingOwnerException::class)
    @ApiResponse(responseCode = "403", description = "Only the listing owner may claim")
    fun onNotListingOwner(): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse("not_listing_owner"))

    @ExceptionHandler(VerificationUnavailableException::class)
    @ApiResponse(responseCode = "503", description = "Verification provider unavailable; review held for retry")
    fun onVerificationUnavailable(): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ErrorResponse("verification_unavailable"))

    data class ClaimResponse(
        val reviewId: UUID,
        val listingId: UUID,
        val submittedBy: UUID,
        val state: String,
        val suggestedVerdict: String?,
    ) {
        companion object {
            fun from(review: HalalCertificationReview): ClaimResponse = ClaimResponse(
                reviewId = review.id,
                listingId = review.listingId,
                submittedBy = review.submittedBy,
                state = review.state.name,
                suggestedVerdict = review.suggestion?.let { it.verdict.name },
            )
        }
    }
}
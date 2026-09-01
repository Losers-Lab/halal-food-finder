package com.tahirslist.bootstrap.verification

import com.tahirslist.application.verification.ReviewNotPendingException
import com.tahirslist.application.verification.ReviewNotFoundException
import com.tahirslist.application.verification.VerificationCommittee
import com.tahirslist.domain.account.Role
import com.tahirslist.domain.verification.HalalCertificationReview
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
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Verification Committee review surface (sc-73): the human decision loop over
 * AI-suggested certifications.
 *
 * Three committee-only operations, all guarded by the JWT `role` claim:
 *   1. GET  /v1/verification-committee/reviews            -> the pending workqueue
 *   2. POST /v1/verification-committee/reviews/{id}/approve -> approve -> listing VERIFIED
 *   3. POST /v1/verification-committee/reviews/{id}/deny    -> deny (records a reason)
 *
 * RBAC: the deny-by-default resource server (sc-131) rejects unauthenticated
 * callers with a generic 401, and the JWT `role` claim must be one of the six
 * MVP roles (RoleClaimValidator). This controller additionally requires the
 * role to be [Role.VERIFICATION_COMMITTEE] — a non-committee (authenticated)
 * caller gets 403 `forbidden`, never a peek at the workqueue. Consistent with
 * the resource server's generic-error discipline: responses never reveal more
 * auth detail than needed.
 */
@RestController
@RequestMapping("/v1/verification-committee")
class VerificationCommitteeController(
    private val committee: VerificationCommittee,
) {

    @GetMapping("/reviews")
    @Operation(summary = "List pending verifications", description = "Verification Committee workqueue: every certification review awaiting a human decision (AI_SUGGESTED).")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Pending reviews", content = [Content(schema = Schema(implementation = ReviewResponse::class))]),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Verification Committee role required"),
        ],
    )
    fun listPending(authentication: JwtAuthenticationToken): List<ReviewResponse> {
        requireCommittee(authentication)
        return committee.listPending().map { ReviewResponse.from(it) }
    }

    @PostMapping("/reviews/{reviewId}/approve")
    @Operation(summary = "Approve a verification", description = "Approves the certification review and promotes the listing to VERIFIED (the only path to a verified listing).")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Approved; listing promoted to VERIFIED", content = [Content(schema = Schema(implementation = ReviewResponse::class))]),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Verification Committee role required"),
            ApiResponse(responseCode = "404", description = "Review not found"),
            ApiResponse(responseCode = "409", description = "Review not pending"),
        ],
    )
    fun approve(
        @PathVariable reviewId: UUID,
        @RequestBody(required = false) request: DecideRequest?,
        authentication: JwtAuthenticationToken,
    ): ReviewResponse {
        requireCommittee(authentication)
        val decided = committee.approve(
            reviewId = reviewId,
            decidedBy = committeeMemberId(authentication),
            reason = request?.reason,
        )
        return ReviewResponse.from(decided)
    }

    @PostMapping("/reviews/{reviewId}/deny")
    @Operation(summary = "Deny a verification", description = "Denies the certification review, recording the reason; the listing stays unverified.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Denied; reason recorded", content = [Content(schema = Schema(implementation = ReviewResponse::class))]),
            ApiResponse(responseCode = "400", description = "A denial reason is required"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Verification Committee role required"),
            ApiResponse(responseCode = "404", description = "Review not found"),
            ApiResponse(responseCode = "409", description = "Review not pending"),
        ],
    )
    fun deny(
        @PathVariable reviewId: UUID,
        @RequestBody request: DecideRequest,
        authentication: JwtAuthenticationToken,
    ): ReviewResponse {
        requireCommittee(authentication)
        val decided = committee.deny(
            reviewId = reviewId,
            decidedBy = committeeMemberId(authentication),
            // A denial without a reason is rejected (the use case enforces non-blank).
            reason = request.reason ?: throw IllegalArgumentException("A denial reason is required."),
        )
        return ReviewResponse.from(decided)
    }

    private fun requireCommittee(authentication: JwtAuthenticationToken) {
        val role = authentication.token.getClaim<String>("role")
        if (role != Role.VERIFICATION_COMMITTEE.name) {
            throw NotVerificationCommitteeException()
        }
    }

    private fun committeeMemberId(authentication: JwtAuthenticationToken): UUID =
        UUID.fromString(authentication.token.subject)

    @ExceptionHandler(NotVerificationCommitteeException::class)
    @ApiResponse(responseCode = "403", description = "Verification Committee role required")
    fun onNotCommittee(): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse("forbidden"))

    @ExceptionHandler(ReviewNotFoundException::class)
    @ApiResponse(responseCode = "404", description = "Review not found")
    fun onReviewNotFound(): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse("review_not_found"))

    @ExceptionHandler(ReviewNotPendingException::class)
    @ApiResponse(responseCode = "409", description = "Review not pending")
    fun onReviewNotPending(): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse("review_not_pending"))

    data class DecideRequest(
        val reason: String? = null,
    )

    data class ReviewResponse(
        val reviewId: UUID,
        val listingId: UUID,
        val submittedBy: UUID,
        val state: String,
        val suggestedVerdict: String?,
        val suggestionConfidence: Double?,
        val suggestionReasoning: String?,
        val decisionOutcome: String?,
        val decisionReason: String?,
        val decidedBy: UUID?,
    ) {
        companion object {
            fun from(review: HalalCertificationReview): ReviewResponse = ReviewResponse(
                reviewId = review.id,
                listingId = review.listingId,
                submittedBy = review.submittedBy,
                state = review.state.name,
                suggestedVerdict = review.suggestion?.verdict?.name,
                suggestionConfidence = review.suggestion?.confidence,
                suggestionReasoning = review.suggestion?.reasoning,
                decisionOutcome = review.decision?.outcome?.let { it.name },
                decisionReason = review.decision?.reason,
                decidedBy = review.decision?.decidedBy,
            )
        }
    }
}
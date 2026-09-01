package com.tahirslist.application.verification

import com.tahirslist.application.listing.RestaurantListingRepository
import com.tahirslist.domain.restaurant.Cuisine
import com.tahirslist.domain.restaurant.CuttingMethod
import com.tahirslist.domain.restaurant.LatLng
import com.tahirslist.domain.restaurant.RestaurantListing
import com.tahirslist.domain.restaurant.VerificationStatus
import com.tahirslist.domain.verification.HalalCertificationReview
import com.tahirslist.domain.verification.SuggestionVerdict
import com.tahirslist.domain.verification.VerificationOutcome
import com.tahirslist.domain.verification.VerificationSuggestion
import com.tahirslist.domain.verification.VerificationState
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.util.UUID

/**
 * sc-73 application use case, TDD:
 *  - the Committee can list the pending (AI_SUGGESTED) workqueue;
 *  - approve walks the state machine to APPROVED AND promotes the listing to
 *    VERIFIED;
 *  - deny walks to DENIED, records the reason, and does NOT promote the listing;
 *  - non-pending / nonexistent reviews are rejected; a blank deny reason is
 *    rejected.
 */
class VerificationCommitteeTest : FunSpec({

    val now = Instant.parse("2026-09-01T12:00:00Z")
    val vcId = UUID.randomUUID()
    val listingId = UUID.randomUUID()
    val ownerId = UUID.randomUUID()

    val reviews = mockk<HalalCertificationReviewRepository>()
    val listings = mockk<RestaurantListingRepository>()
    val committee = VerificationCommittee(reviews, listings)

    fun pendingReview(id: UUID): HalalCertificationReview = HalalCertificationReview(
        id = id,
        listingId = listingId,
        submittedBy = ownerId,
        state = VerificationState.AI_SUGGESTED,
        createdAt = now,
        updatedAt = now,
        suggestion = VerificationSuggestion(SuggestionVerdict.NEEDS_REVIEW, 0.4, "unclear"),
        aiConsentGivenAt = now,
    )

    fun aListing(status: VerificationStatus = VerificationStatus.UNVERIFIED): RestaurantListing =
        RestaurantListing.fromStorage(
            id = listingId,
            name = "Halal Grill",
            address = "123 Main St",
            location = LatLng(40.7128, -74.0060),
            cuisine = Cuisine("mediterranean"),
            cuttingMethod = CuttingMethod.HAND_CUT,
            ownerId = ownerId,
            brandId = null,
            provenance = null,
            verificationStatus = status,
            createdAt = Instant.now(),
        )

    beforeTest { clearMocks(reviews, listings) }

    test("listPending returns every review awaiting a human decision") {
        val r1 = pendingReview(UUID.randomUUID())
        val r2 = pendingReview(UUID.randomUUID())
        every { reviews.findByState(VerificationState.AI_SUGGESTED) } returns listOf(r1, r2)

        committee.listPending() shouldBe listOf(r1, r2)
        verify { reviews.findByState(VerificationState.AI_SUGGESTED) }
    }

    test("approve walks the review to APPROVED and promotes the listing to VERIFIED") {
        val reviewId = UUID.randomUUID()
        val pending = pendingReview(reviewId)
        every { reviews.findById(reviewId) } returns pending
        every { reviews.save(any()) } returnsArgument 0
        every { listings.updateVerificationStatus(listingId, VerificationStatus.VERIFIED) } returns aListing(VerificationStatus.VERIFIED)

        val result = committee.approve(reviewId, vcId, reason = "cert matches listing", now = now)

        result.state shouldBe VerificationState.APPROVED
        val decision = result.decision!!
        decision.outcome shouldBe VerificationOutcome.APPROVED
        decision.decidedBy shouldBe vcId
        decision.reason shouldBe "cert matches listing"
        verify { reviews.save(match { it.state == VerificationState.APPROVED }) }
        verify { listings.updateVerificationStatus(listingId, VerificationStatus.VERIFIED) }
    }

    test("approve records the certifier and expiry on the approved review (sc-73 read surface)") {
        val reviewId = UUID.randomUUID()
        val expiresOn = java.time.LocalDate.of(2027, 1, 12)
        every { reviews.findById(reviewId) } returns pendingReview(reviewId)
        every { reviews.save(any()) } returnsArgument 0
        every { listings.updateVerificationStatus(listingId, VerificationStatus.VERIFIED) } returns aListing(VerificationStatus.VERIFIED)

        val result = committee.approve(reviewId, vcId, now = now, certifier = "HFSAA", expiresOn = expiresOn)

        result.certifier shouldBe "HFSAA"
        result.expiresOn shouldBe expiresOn
        verify { reviews.save(match { it.certifier == "HFSAA" && it.expiresOn == expiresOn }) }
    }

    test("approve rejects a review that is not pending") {
        val reviewId = UUID.randomUUID()
        val alreadyApproved = pendingReview(reviewId)
            .beginHumanReview(now)
            .approve(decidedBy = vcId, now = now)
        every { reviews.findById(reviewId) } returns alreadyApproved

        val ex = shouldThrow<ReviewNotPendingException> {
            committee.approve(reviewId, vcId, now = now)
        }

        ex.state shouldBe VerificationState.APPROVED
        verify(exactly = 0) { reviews.save(any()) }
        verify(exactly = 0) { listings.updateVerificationStatus(any(), any()) }
    }

    test("approve of a nonexistent review is rejected") {
        val reviewId = UUID.randomUUID()
        every { reviews.findById(reviewId) } returns null

        shouldThrow<ReviewNotFoundException> {
            committee.approve(reviewId, vcId, now = now)
        }

        verify(exactly = 0) { reviews.save(any()) }
        verify(exactly = 0) { listings.updateVerificationStatus(any(), any()) }
    }

    test("approve fails loudly if the listing was removed after the review was created") {
        val reviewId = UUID.randomUUID()
        every { reviews.findById(reviewId) } returns pendingReview(reviewId)
        every { reviews.save(any()) } returnsArgument 0
        every { listings.updateVerificationStatus(listingId, VerificationStatus.VERIFIED) } returns null

        shouldThrow<ReviewNotFoundException> {
            committee.approve(reviewId, vcId, now = now)
        }
    }

    test("deny walks the review to DENIED with the reason and does NOT promote the listing") {
        val reviewId = UUID.randomUUID()
        val pending = pendingReview(reviewId)
        every { reviews.findById(reviewId) } returns pending
        every { reviews.save(any()) } returnsArgument 0

        val result = committee.deny(reviewId, vcId, reason = "cert image unreadable", now = now)

        result.state shouldBe VerificationState.DENIED
        val decision = result.decision!!
        decision.outcome shouldBe VerificationOutcome.DENIED
        decision.decidedBy shouldBe vcId
        decision.reason shouldBe "cert image unreadable"
        verify { reviews.save(match { it.state == VerificationState.DENIED }) }
        verify(exactly = 0) { listings.updateVerificationStatus(any(), any()) }
    }

    test("deny rejects a blank reason") {
        val reviewId = UUID.randomUUID()
        every { reviews.findById(reviewId) } returns pendingReview(reviewId)

        shouldThrow<IllegalArgumentException> {
            committee.deny(reviewId, vcId, reason = "   ", now = now)
        }

        verify(exactly = 0) { reviews.save(any()) }
    }
})
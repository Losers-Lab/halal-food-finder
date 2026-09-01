package com.tahirslist.application.verification

import com.tahirslist.application.favorite.ListingNotFoundException
import com.tahirslist.application.listing.RestaurantListingRepository
import com.tahirslist.domain.restaurant.Cuisine
import com.tahirslist.domain.restaurant.LatLng
import com.tahirslist.domain.restaurant.RestaurantListing
import com.tahirslist.domain.restaurant.VerificationStatus
import com.tahirslist.domain.verification.HalalCertificationReview
import com.tahirslist.domain.verification.SuggestionVerdict
import com.tahirslist.domain.verification.VerificationSuggestion
import com.tahirslist.domain.verification.VerificationState
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.util.UUID

/**
 * Owner-claim (sc-46) application use case, TDD:
 *
 *  - an authenticated listing **owner** submits proof of ownership + a certification
 *    image;
 *  - the image is stored (MinIO via the [CertificationImageStorage] seam);
 *  - the review is driven SUBMITTED -> AI_REVIEW -> AI_SUGGESTED via the sc-117
 *    [RequestVerification] seam and the resulting review is persisted;
 *  - non-owners / unknown listings are rejected; a provider outage holds the review
 *    in AI_REVIEW for a retry (never drops the claim, never auto-grants).
 */
class ClaimListingTest : FunSpec({

    val now = Instant.parse("2026-08-31T12:00:00Z")

    val listings = mockk<RestaurantListingRepository>()
    val reviews = mockk<HalalCertificationReviewRepository>()
    val certificates = mockk<CertificationImageStorage>()
    val requestVerification = mockk<RequestVerification>()
    val claimListing = ClaimListing(listings, reviews, certificates, requestVerification)

    val listingId = UUID.randomUUID()
    val claimerId = UUID.randomUUID()
    val proof = " I own this restaurant; business license TAH-2024-118. "
    val contentType = "image/jpeg"
    val imageBytes = byteArrayOf(1, 2, 3, 4)
    val consentGivenAt = Instant.parse("2026-09-01T12:30:00Z")

    beforeTest { clearMocks(listings, reviews, certificates, requestVerification) }

    test("an owner's claim stores the certification, drives AI review to AI_SUGGESTED, and persists") {
        every { listings.findById(listingId) } returns aListing(listingId, ownerId = claimerId)
        every { certificates.save(listingId, contentType, imageBytes) } returns Unit
        val suggested = HalalCertificationReview.create(listingId, claimerId, now)
            .beginAiReview(now)
            .recordAiSuggestion(VerificationSuggestion(SuggestionVerdict.NEEDS_REVIEW, 0.4), now)
        every { requestVerification.execute(listingId, claimerId, any(), now, aiConsentGivenAt = now) } returns suggested
        every { reviews.save(suggested) } returns suggested

        val result = claimListing.execute(listingId, claimerId, proof, true, contentType, imageBytes, now)

        result shouldBe suggested
        result.state shouldBe VerificationState.AI_SUGGESTED
        verify { certificates.save(listingId, contentType, imageBytes) }
        verify { requestVerification.execute(listingId, claimerId, any(), now, aiConsentGivenAt = now) }
        verify { reviews.save(suggested) }
    }

    test("a claim without explicit AI-analysis consent is rejected before any storage or persistence") {
        every { listings.findById(listingId) } returns aListing(listingId, ownerId = claimerId)

        shouldThrow<IllegalArgumentException> {
            claimListing.execute(listingId, claimerId, proof, false, contentType, imageBytes, now)
        }

        // the image must never be archived or uploaded without consent
        verify(exactly = 0) { certificates.save(any(), any(), any()) }
        verify(exactly = 0) { requestVerification.execute(any(), any(), any(), now) }
        verify(exactly = 0) { reviews.save(any()) }
    }

    test("an AI APPROVE suggestion is carried but never elevates the review past AI_SUGGESTED") {
        every { listings.findById(listingId) } returns aListing(listingId, ownerId = claimerId)
        every { certificates.save(any(), any(), any()) } returns Unit
        val approve = HalalCertificationReview.create(listingId, claimerId, now)
            .beginAiReview(now)
            .recordAiSuggestion(VerificationSuggestion(SuggestionVerdict.APPROVE, 0.95), now)
        every { requestVerification.execute(any(), any(), any(), now, aiConsentGivenAt = now) } returns approve
        every { reviews.save(approve) } returns approve

        val result = claimListing.execute(listingId, claimerId, proof, true, contentType, imageBytes, now)

        result.state shouldBe VerificationState.AI_SUGGESTED
        result.suggestion!!.verdict shouldBe SuggestionVerdict.APPROVE
        // the listing must NOT be auto-verified: the human (sc-73) decides that.
        result.state shouldNotBe VerificationState.APPROVED
    }

    test("a claim from a non-owner is rejected and nothing is stored or persisted") {
        val owner = UUID.randomUUID()
        val intruder = UUID.randomUUID()
        every { listings.findById(listingId) } returns aListing(listingId, ownerId = owner)

        shouldThrow<NotListingOwnerException> {
            claimListing.execute(listingId, intruder, proof, true, contentType, imageBytes, now)
        }

        verify(exactly = 0) { certificates.save(any(), any(), any()) }
        verify(exactly = 0) { reviews.save(any()) }
        verify(exactly = 0) { requestVerification.execute(any(), any(), any(), now) }
    }

    test("a claim for an unknown listing is rejected") {
        every { listings.findById(listingId) } returns null
        val stranger = UUID.randomUUID()

        shouldThrow<ListingNotFoundException> {
            claimListing.execute(listingId, stranger, proof, true, contentType, imageBytes, now)
        }

        verify(exactly = 0) { certificates.save(any(), any(), any()) }
        verify(exactly = 0) { reviews.save(any()) }
    }

    test("blank proof is rejected as invalid input") {
        every { listings.findById(listingId) } returns aListing(listingId, ownerId = claimerId)

        shouldThrow<IllegalArgumentException> {
            claimListing.execute(listingId, claimerId, "   ", true, contentType, imageBytes, now)
        }

        verify(exactly = 0) { certificates.save(any(), any(), any()) }
        verify(exactly = 0) { reviews.save(any()) }
    }

    test("a provider outage holds the review in AI_REVIEW and raises verification_unavailable") {
        every { listings.findById(listingId) } returns aListing(listingId, ownerId = claimerId)
        every { certificates.save(any(), any(), any()) } returns Unit
        every { requestVerification.execute(any(), any(), any(), now, aiConsentGivenAt = now) } throws VerificationProviderException("provider unreachable")
        every { reviews.save(match { it.state == VerificationState.AI_REVIEW }) } returnsArgument 0

        val ex = shouldThrow<VerificationUnavailableException> {
            claimListing.execute(listingId, claimerId, proof, true, contentType, imageBytes, now)
        }

        ex.cause!!.message shouldBe "provider unreachable"
        // the claim is not dropped: a durable AI_REVIEW record was saved for a later retry
        verify(exactly = 1) { reviews.save(match { it.state == VerificationState.AI_REVIEW }) }
    }
})

private fun aListing(id: UUID, ownerId: UUID): RestaurantListing = RestaurantListing.fromStorage(
    id = id,
    name = "Halal Grill",
    address = "123 Main St",
    location = LatLng(40.7128, -74.0060),
    cuisine = Cuisine("mediterranean"),
    isHandCut = true,
    ownerId = ownerId,
    brandId = null,
    provenance = null,
    verificationStatus = VerificationStatus.UNVERIFIED,
    createdAt = Instant.now(),
)
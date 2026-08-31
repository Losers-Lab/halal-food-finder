package com.tahirslist.application.verification

import com.tahirslist.domain.verification.CertificationImage
import com.tahirslist.domain.verification.SuggestionVerdict
import com.tahirslist.domain.verification.VerificationSuggestion
import com.tahirslist.domain.verification.VerificationState
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.util.UUID

/**
 * RequestVerification drives the forward verification path through the
 * VerificationProvider seam (mock here — no live provider call):
 *
 *   SUBMITTED -> AI_REVIEW -> (AI suggests) -> AI_SUGGESTED
 *
 * Failure is honested: if the provider throws, the operation aborts and no
 * AI_SUGGESTED review is produced — the review stays AI_REVIEW for a retry,
 * never auto-advances on an outage.
 */
class RequestVerificationTest : FunSpec({

    val now = Instant.parse("2026-08-31T12:00:00Z")
    val image = CertificationImage("image/jpeg", byteArrayOf(1, 2, 3))
    val listingId = UUID.randomUUID()
    val submittedBy = UUID.randomUUID()

    test("walks SUBMITTED -> AI_REVIEW -> AI_SUGGESTED with a conservative suggestion") {
        val provider = mockk<VerificationProvider>()
        val suggestion = VerificationSuggestion(SuggestionVerdict.NEEDS_REVIEW, 0.4)
        every { provider.suggest(image) } returns suggestion

        val result = RequestVerification(provider).execute(listingId, submittedBy, image, now)

        // Final state is AI_SUGGESTED and the AI's conservative suggestion is recorded.
        result.state shouldBe VerificationState.AI_SUGGESTED
        result.suggestion shouldBe suggestion
        result.listingId shouldBe listingId
        result.submittedBy shouldBe submittedBy
        verify { provider.suggest(image) }
    }

    test("the use case overrides nothing: a provider APPROVE suggestion is carried, not elevated") {
        val provider = mockk<VerificationProvider>()
        every { provider.suggest(image) } returns VerificationSuggestion(SuggestionVerdict.APPROVE, 0.95)

        val result = RequestVerification(provider).execute(listingId, submittedBy, image, now)

        // The AI can only ever *suggest* — the review must still await human review.
        result.state shouldBe VerificationState.AI_SUGGESTED
        result.suggestion!!.verdict shouldBe SuggestionVerdict.APPROVE
    }

    test("a provider failure aborts without producing an AI_SUGGESTED review") {
        val provider = mockk<VerificationProvider>()
        every { provider.suggest(image) } throws VerificationProviderException("provider unreachable")

        val ex = shouldThrow<VerificationProviderException> {
            RequestVerification(provider).execute(listingId, submittedBy, image, now)
        }

        ex.message shouldBe "provider unreachable"
    }
})
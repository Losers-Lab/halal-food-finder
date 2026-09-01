package com.tahirslist.domain.verification

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.util.UUID

/**
 * Pins the SUBMITTED -> AI_REVIEW -> AI_SUGGESTED -> HUMAN_REVIEW ->
 * {APPROVED|DENIED} (+REVERSED) state machine on the review aggregate.
 *
 * A transition the state machine does not allow must throw
 * IllegalStateException, so out-of-order drivers (a bug that would otherwise
 * silently corrupt the review) fail loudly.
 */
class HalalCertificationReviewTest : FunSpec({

    val now = Instant.parse("2026-08-31T12:00:00Z")
    fun review(state: VerificationState = VerificationState.SUBMITTED) =
        HalalCertificationReview.create(
            listingId = UUID.randomUUID(),
            submittedBy = UUID.randomUUID(),
            now = now,
        ).let {
            when (state) {
                VerificationState.SUBMITTED -> it
                VerificationState.AI_REVIEW -> it.beginAiReview()
                VerificationState.AI_SUGGESTED -> it.beginAiReview().recordAiSuggestion(
                    VerificationSuggestion(SuggestionVerdict.NEEDS_REVIEW, 0.5), now)
                VerificationState.HUMAN_REVIEW ->
                    it.beginAiReview().recordAiSuggestion(
                        VerificationSuggestion(SuggestionVerdict.NEEDS_REVIEW, 0.5), now)
                        .beginHumanReview()
                else -> error("helper only builds forward states")
            }
        }

    test("a new review starts SUBMITTED") {
        review().state shouldBe VerificationState.SUBMITTED
    }

    test("consent is recorded on the review and survives every transition") {
        val consentGivenAt = now
        val started = HalalCertificationReview.create(
            listingId = UUID.randomUUID(),
            submittedBy = UUID.randomUUID(),
            now = now,
            aiConsentGivenAt = consentGivenAt,
        )
        started.aiConsentGivenAt shouldBe consentGivenAt

        val suggested = started.beginAiReview(now)
            .recordAiSuggestion(VerificationSuggestion(SuggestionVerdict.NEEDS_REVIEW, 0.5), now)
        suggested.aiConsentGivenAt shouldBe consentGivenAt

        val human = suggested.beginHumanReview(now).approve(UUID.randomUUID(), "ok", now)
        human.aiConsentGivenAt shouldBe consentGivenAt
    }

    test("a review can be created without consent (null when not given)") {
        HalalCertificationReview.create(UUID.randomUUID(), UUID.randomUUID(), now)
            .aiConsentGivenAt shouldBe null
    }

    test("SUBMITTED -> AI_REVIEW -> AI_SUGGESTED drives the forward path") {
        val started = review(VerificationState.SUBMITTED).beginAiReview()
        started.state shouldBe VerificationState.AI_REVIEW

        val suggestion = VerificationSuggestion(SuggestionVerdict.APPROVE, 0.95)
        val suggested = started.recordAiSuggestion(suggestion, now)
        suggested.state shouldBe VerificationState.AI_SUGGESTED
        suggested.suggestion shouldBe suggestion
    }

    test("AI_SUGGESTED -> HUMAN_REVIEW -> APPROVED records the approving decision") {
        val human = review(VerificationState.AI_SUGGESTED).beginHumanReview()
        human.state shouldBe VerificationState.HUMAN_REVIEW

        val vc = UUID.randomUUID()
        val approved = human.approve(vc, "cert matches the listed restaurant", now)
        approved.state shouldBe VerificationState.APPROVED
        approved.decision!!.outcome shouldBe VerificationOutcome.APPROVED
        approved.decision!!.decidedBy shouldBe vc
        approved.decision!!.reason shouldBe "cert matches the listed restaurant"
    }

    test("AI_SUGGESTED -> HUMAN_REVIEW -> DENIED records the denying decision") {
        val human = review(VerificationState.AI_SUGGESTED).beginHumanReview()

        val denied = human.deny(UUID.randomUUID(), "cert expired", now)

        denied.state shouldBe VerificationState.DENIED
        denied.decision!!.outcome shouldBe VerificationOutcome.DENIED
    }

    test("APPROVED can be REVERSED (grant revoked later)") {
        val approved = review(VerificationState.AI_SUGGESTED)
            .beginHumanReview().approve(UUID.randomUUID(), "ok", now)

        val reversed = approved.reverse(UUID.randomUUID(), "cert revoked by issuer", now)

        reversed.state shouldBe VerificationState.REVERSED
    }

    test("DENIED can be REVERSED (wrongful denial overturned)") {
        val denied = review(VerificationState.AI_SUGGESTED)
            .beginHumanReview().deny(UUID.randomUUID(), "mistaken denial", now)

        denied.reverse(UUID.randomUUID(), "overturned", now).state shouldBe VerificationState.REVERSED
    }

    test("REVERSED is terminal — no forward transition is legal") {
        val reversed = review(VerificationState.AI_SUGGESTED)
            .beginHumanReview().deny(UUID.randomUUID(), "mistaken denial", now)
            .reverse(UUID.randomUUID(), "overturned", now)

        shouldThrow<IllegalStateException> { reversed.beginHumanReview() }
        shouldThrow<IllegalStateException> { reversed.approve(UUID.randomUUID(), "x", now) }
    }

    test("a suggestion records the provider's conservative suggestion") {
        val started = review(VerificationState.SUBMITTED).beginAiReview()
        val suggestion = VerificationSuggestion(SuggestionVerdict.NEEDS_REVIEW, 0.4)

        started.recordAiSuggestion(suggestion, now).suggestion shouldBe suggestion
    }

    test("APPROVED does NOT accept another approval (already terminal)") {
        val approved = review(VerificationState.AI_SUGGESTED)
            .beginHumanReview().approve(UUID.randomUUID(), "ok", now)

        shouldThrow<IllegalStateException> { approved.approve(UUID.randomUUID(), "again", now) }
    }

    test("reversing before a review was human-decided is illegal") {
        val suggested = review(VerificationState.AI_SUGGESTED)

        shouldThrow<IllegalStateException> { suggested.reverse(UUID.randomUUID(), "nope", now) }
    }
})
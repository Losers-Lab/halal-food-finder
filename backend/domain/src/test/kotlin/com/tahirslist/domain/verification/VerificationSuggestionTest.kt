package com.tahirslist.domain.verification

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Value-object invariants: confidence is a 0..1 fraction and the cert image is
 * non-blank/non-empty — the seam must never carry a garbage or empty payload.
 */
class VerificationSuggestionTest : FunSpec({

    test("confidence is clamped into the 0..1 range on construction") {
        VerificationSuggestion(SuggestionVerdict.APPROVE, 1.2).confidence shouldBe 1.0
        VerificationSuggestion(SuggestionVerdict.DENY, -0.3).confidence shouldBe 0.0
        VerificationSuggestion(SuggestionVerdict.NEEDS_REVIEW, 0.55).confidence shouldBe 0.55
    }

    test("ModelJudgment confidence is clamped into the 0..1 range on construction") {
        ModelJudgment(ModelVerdict.CERT_VALID, 1.7).confidence shouldBe 1.0
        ModelJudgment(ModelVerdict.CERT_VALID, -5.0).confidence shouldBe 0.0
    }

    test("a blank content type is rejected") {
        shouldThrow<IllegalArgumentException> {
            CertificationImage("  ", byteArrayOf(1, 2, 3))
        }
    }

    test("an empty image payload is rejected") {
        shouldThrow<IllegalArgumentException> {
            CertificationImage("image/jpeg", ByteArray(0))
        }
    }

    test("a valid certification image is accepted") {
        CertificationImage("image/jpeg", byteArrayOf(1, 2, 3)).contentType shouldBe "image/jpeg"
    }
})
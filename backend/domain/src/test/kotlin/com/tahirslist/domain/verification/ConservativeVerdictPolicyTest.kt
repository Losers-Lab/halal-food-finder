package com.tahirslist.domain.verification

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Pins the conservative-verdict policy: the AI's disposition is *suggested* and
 * only ever forwarded to a human as AI_SUGGESTED. The policy favours a human
 * NEEDS_REVIEW over a false-positive APPROVE (trust rule — amanah): ONLY a
 * high-confidence, explicitly-valid judgment may be suggested APPROVE; anything
 * uncertain, low-confidence, or unclassifiable defers to a human.
 */
class ConservativeVerdictPolicyTest : FunSpec({

    fun judge(verdict: ModelVerdict, confidence: Double) = ModelJudgment(verdict, confidence)

    test("a high-confidence CERT_VALID judgment is suggested APPROVE") {
        apply(judge(ModelVerdict.CERT_VALID, 0.95)).verdict shouldBe SuggestionVerdict.APPROVE
    }

    test("a high-confidence NOT_VALID judgment is suggested DENY") {
        apply(judge(ModelVerdict.NOT_VALID, 0.97)).verdict shouldBe SuggestionVerdict.DENY
    }

    test("an INCONCLUSIVE judgment is NEVER suggested APPROVE — always human review") {
        apply(judge(ModelVerdict.INCONCLUSIVE, 1.0)).verdict shouldBe SuggestionVerdict.NEEDS_REVIEW
    }

    test("a CERT_VALID judgment below the high-confidence threshold defers to a human") {
        // 0.89 is below HIGH_CONFIDENCE (0.9) — even though the model leaned
        // valid, a wrongly-granted badge is a trust failure; defer.
        apply(judge(ModelVerdict.CERT_VALID, 0.89)).verdict shouldBe SuggestionVerdict.NEEDS_REVIEW
    }

    test("a NOT_VALID judgment below the high-confidence threshold defers to a human") {
        apply(judge(ModelVerdict.NOT_VALID, 0.5)).verdict shouldBe SuggestionVerdict.NEEDS_REVIEW
    }

    test("a zero-confidence CERT_VALID judgment defers to a human") {
        apply(judge(ModelVerdict.CERT_VALID, 0.0)).verdict shouldBe SuggestionVerdict.NEEDS_REVIEW
    }

    test("an exactly-threshold valid judgment is suggested APPROVE (>=, not >)") {
        apply(judge(ModelVerdict.CERT_VALID, ConservativeVerdictPolicy.HIGH_CONFIDENCE))
            .verdict shouldBe SuggestionVerdict.APPROVE
    }

    test("the boundary between suggested and deferred is the high-confidence threshold") {
        ConservativeVerdictPolicy.HIGH_CONFIDENCE shouldBe 0.9
    }
})

private fun apply(judgment: ModelJudgment) = ConservativeVerdictPolicy.apply(judgment)
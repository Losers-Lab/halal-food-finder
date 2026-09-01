package com.tahirslist.application.verification

import com.tahirslist.domain.verification.CertificationImage
import com.tahirslist.domain.verification.SuggestionVerdict
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The dev/test safe-default provider (sc-46): when no autonomous verification
 * provider is configured, every claim is suggested NEEDS_REVIEW so a human
 * (sc-73) always decides. This is the "when in doubt, human" trust rule made
 * structural for the no-AI boot path.
 */
class DeferToHumanProviderTest : FunSpec({

    val provider = DeferToHumanProvider()
    val image = CertificationImage("image/jpeg", byteArrayOf(1, 2, 3))

    test("always suggests NEEDS_REVIEW, never APPROVE or DENY") {
        val suggestion = provider.suggest(image)

        suggestion.verdict shouldBe SuggestionVerdict.NEEDS_REVIEW
        suggestion.confidence shouldBe 0.0
    }
})
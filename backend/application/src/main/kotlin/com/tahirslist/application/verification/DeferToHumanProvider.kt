package com.tahirslist.application.verification

import com.tahirslist.domain.verification.CertificationImage
import com.tahirslist.domain.verification.SuggestionVerdict
import com.tahirslist.domain.verification.VerificationSuggestion

/**
 * The safe default [VerificationProvider] when no autonomous provider is
 * configured and wired (i.e. no `app.verification.hosted.endpoint`). It always
 * suggests [SuggestionVerdict.NEEDS_REVIEW], so a claim reaches AI_SUGGESTED and
 * a human (sc-73) independently decides — never a spoon-fed APPROVE/DENY from
 * the void. This makes "when in doubt, human" structural for the no-AI boot path
 * (dev/test, or deployments that deliberately run review fully manual).
 */
class DeferToHumanProvider : VerificationProvider {
    override fun suggest(image: CertificationImage): VerificationSuggestion =
        VerificationSuggestion(
            verdict = SuggestionVerdict.NEEDS_REVIEW,
            confidence = 0.0,
            reasoning = "No autonomous verification provider configured; deferred to human review.",
        )
}
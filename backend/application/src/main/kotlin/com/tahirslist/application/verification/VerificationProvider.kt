package com.tahirslist.application.verification

import com.tahirslist.domain.verification.CertificationImage
import com.tahirslist.domain.verification.VerificationSuggestion

/**
 * The verification seam (hexagonal "out" port). Implemented by the hosted-AI
 * adapter ([HostedVisionAdapter]) by default and swappable by config — the same
 * port/adapter shape as [com.tahirslist.application.geo.GeocoderPort] and the
 * image port (docs/reviews/sc-138-external-services.md §3).
 *
 * Contract:
 *  - `suggest` returns a **conservative** [VerificationSuggestion] for the
 *    certification image — the AI's *suggested* disposition, NEVER a final
 *    VERIFIED status. A suggestion only moves the review to AI_SUGGESTED; a
 *    human (Verification Committee) must confirm (founder mandate: "AI-suggests
 *    -> VC-approves, never ship AI alone").
 *  - The adapter is held to [com.tahirslist.domain.verification.ConservativeVerdictPolicy]:
 *    only a high-confidence, explicitly-valid judgment may be suggested APPROVE;
 *    anything uncertain defers to [SuggestionVerdict.NEEDS_REVIEW].
 *  - provider failures (non-2xx, timeout, protocol error) throw
 *    [VerificationProviderException]; the caller treats that as unavailability,
 *    not as a verdict — the review stays AI_REVIEW and can be retried.
 */
interface VerificationProvider {
    fun suggest(image: CertificationImage): VerificationSuggestion
}
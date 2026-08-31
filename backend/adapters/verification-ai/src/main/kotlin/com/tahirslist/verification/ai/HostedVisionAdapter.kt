package com.tahirslist.verification.ai

import com.tahirslist.application.verification.VerificationProvider
import com.tahirslist.domain.verification.CertificationImage
import com.tahirslist.domain.verification.ConservativeVerdictPolicy
import com.tahirslist.domain.verification.VerificationSuggestion

/**
 * The default [VerificationProvider]: a hosted multimodal vision model judges the
 * certification image, and the [ConservativeVerdictPolicy] turns that raw judgment
 * into a conservative [VerificationSuggestion].
 *
 * Responsibility split:
 *  - this adapter owns the analysis prompt and the conservative mapping;
 *  - the [VisionModelClient] transport owns endpoint/model/HTTP (no live call is
 *    ever made from the adapter itself, so the adapter is unit-tested with a mock).
 */
class HostedVisionAdapter(
    private val client: VisionModelClient,
    private val policy: ConservativeVerdictPolicy = ConservativeVerdictPolicy,
) : VerificationProvider {

    override fun suggest(image: CertificationImage): VerificationSuggestion {
        val judgment = client.analyze(PromptTemplate.build(), image)
        return policy.apply(judgment)
    }
}
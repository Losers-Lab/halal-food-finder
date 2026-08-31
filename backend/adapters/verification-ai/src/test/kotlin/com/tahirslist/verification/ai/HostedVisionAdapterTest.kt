package com.tahirslist.verification.ai

import com.tahirslist.application.verification.VerificationProvider
import com.tahirslist.domain.verification.CertificationImage
import com.tahirslist.domain.verification.ModelJudgment
import com.tahirslist.domain.verification.ModelVerdict
import com.tahirslist.domain.verification.SuggestionVerdict
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

/**
 * HostedVisionAdapter is the default VerificationProvider. The vision transport
 * (VisionModelClient) is mocked here — no live provider call. The adapter's job
 * is to ask the model about the cert image and push the model's raw judgment
 * through the conservative policy so the Review always sees a safe suggestion.
 */
class HostedVisionAdapterTest : FunSpec({

    val image = CertificationImage("image/jpeg", byteArrayOf(10, 20, 30))

    fun adapter(client: VisionModelClient) =
        HostedVisionAdapter(client = client)

    test("is a VerificationProvider (the pluggable seam contract)") {
        adapter(mockk()).shouldBeInstanceOf<VerificationProvider>()
    }

    test("asks the vision client for a judgment of the cert image") {
        val client = mockk<VisionModelClient>()
        every { client.analyze(any(), image) } returns ModelJudgment(ModelVerdict.CERT_VALID, 0.95)

        adapter(client).suggest(image)

        verify { client.analyze(any(), image) }
    }

    test("applies the conservative policy to a high-confidence valid judgment") {
        val client = mockk<VisionModelClient>()
        every { client.analyze(any(), image) } returns ModelJudgment(ModelVerdict.CERT_VALID, 0.95)

        adapter(client).suggest(image).verdict shouldBe SuggestionVerdict.APPROVE
    }

    test("defers an ambiguous model judgment to human review via the policy") {
        val client = mockk<VisionModelClient>()
        every { client.analyze(any(), image) } returns ModelJudgment(ModelVerdict.INCONCLUSIVE, 1.0)

        adapter(client).suggest(image).verdict shouldBe SuggestionVerdict.NEEDS_REVIEW
    }
})
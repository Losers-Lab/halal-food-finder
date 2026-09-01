package com.tahirslist.bootstrap.verification

import com.tahirslist.bootstrap.image.NoS3EndpointCondition
import com.tahirslist.application.listing.RestaurantListingRepository
import com.tahirslist.application.verification.CertificationImageStorage
import com.tahirslist.application.verification.ClaimListing
import com.tahirslist.application.verification.DeferToHumanProvider
import com.tahirslist.application.verification.HalalCertificationReviewRepository
import com.tahirslist.application.verification.RequestVerification
import com.tahirslist.application.verification.VerificationProvider
import com.tahirslist.domain.verification.ConservativeVerdictPolicy
import com.tahirslist.verification.ai.HostedVisionAdapter
import com.tahirslist.verification.ai.HostedVisionConfig
import com.tahirslist.verification.ai.RestVisionModelClient
import com.tahirslist.verification.ai.VisionModelClient
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Conditional
import org.springframework.context.annotation.Configuration

/**
 * Verification wiring for the owner-claim vertical (sc-46). Wires the sc-117
 * provider seam into the app, plus the claim write-path. Lives in bootstrap so the
 * framework-free [ClaimListing] use case is available to [VerificationClaimController].
 *
 * Provider selection (mutually exclusive on the `app.verification.hosted.endpoint`
 * property):
 *  - When set: the hosted multimodal provider ([HostedVisionAdapter]) is the active
 *    [VerificationProvider] — the ratified Gemini 2.5 Flash paid tier.
 *  - When unset: the safe [DeferToHumanProvider] is active — every claim is
 *    suggested NEEDS_REVIEW so a human (sc-73) independently decides. This keeps
 *    dev/test (and deployments with no AI configured) bootable while NEVER
 *    auto-granting verification. "When in doubt, human" is structural, not best-effort.
 */
@Configuration
@EnableConfigurationProperties(HostedVisionProperties::class)
class VerificationConfig {

    @Bean
    @ConditionalOnProperty(prefix = "app.verification.hosted", name = ["endpoint"], havingValue = "false", matchIfMissing = true)
    fun deferToHumanProvider(): VerificationProvider = DeferToHumanProvider()

    @Bean
    @ConditionalOnProperty(prefix = "app.verification.hosted", name = ["endpoint"])
    fun hostedVisionAdapter(client: VisionModelClient): VerificationProvider =
        HostedVisionAdapter(client, ConservativeVerdictPolicy)

    @Bean
    @ConditionalOnProperty(prefix = "app.verification.hosted", name = ["endpoint"])
    fun visionModelClient(props: HostedVisionProperties): VisionModelClient =
        RestVisionModelClient(HostedVisionConfig(
            endpoint = props.endpoint!!,
            modelName = props.modelName,
            apiKey = props.apiKey,
            timeoutMillis = props.timeoutMillis,
        ))

    @Bean
    fun requestVerification(provider: VerificationProvider): RequestVerification =
        RequestVerification(provider)

    @Bean
    fun claimListing(
        listings: RestaurantListingRepository,
        reviews: HalalCertificationReviewRepository,
        certificates: CertificationImageStorage,
        requestVerification: RequestVerification,
    ): ClaimListing = ClaimListing(listings, reviews, certificates, requestVerification)

    @Bean
    @Conditional(NoS3EndpointCondition::class)
    fun inMemoryCertificationImageStorage(): CertificationImageStorage =
        com.tahirslist.application.verification.InMemoryCertificationImageStorage()
}

/**
 * Config properties for the hosted verification provider. [endpoint] gates whether
 * the hosted adapter is active; [apiKey] is read from a secret source at runtime
 * (env/secret manager) and is intentionally NOT a baked literal.
 */
@ConfigurationProperties(prefix = "app.verification.hosted")
data class HostedVisionProperties(
    val endpoint: String? = null,
    val modelName: String = "gemini-2.5-flash",
    val apiKey: String? = null,
    val timeoutMillis: Long = 5_000,
)
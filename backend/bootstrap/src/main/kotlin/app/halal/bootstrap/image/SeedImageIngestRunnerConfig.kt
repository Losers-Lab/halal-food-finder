package app.halal.bootstrap.image

import app.halal.application.image.ImageFetcher
import app.halal.application.image.ImagePort
import app.halal.application.image.IngestHeroImage
import app.halal.application.image.IngestSeedHeroPhotos
import app.halal.application.image.SeedPhotoListingResolver
import app.halal.application.image.SeedPhotoIngestResult
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.Resource
import org.springframework.util.StreamUtils

/**
 * Seed hero-photo ingest, gated behind `app.images.seed-ingest.enabled=true`
 * (OFF by default — boot tests and normal dev should not hit the network or a
 * licensed photo CDN). When enabled it:
 *   1. reads the manifest resource [manifest],
 *   2. resolves each row to a seed listing (persistence resolver),
 *   3. fetches + stores FULL + THUMBNAIL per row (IngestSeedHeroPhotos).
 * Per-row failures are reported, not fatal (isolated in the orchestrator).
 *
 * docs/design/sc-157-image-variants.md §"Ingest use cases".
 */
@Configuration
class SeedImageIngestRunnerConfig {

    @Bean
    @ConditionalOnProperty(prefix = "app.images.seed-ingest", name = ["enabled"], havingValue = "true")
    fun seedImageIngestRunner(
        manifestParser: SeedPhotoManifestParser,
        resolver: SeedPhotoListingResolver,
        fetcher: ImageFetcher,
        port: ImagePort,
        @Value("\${app.images.seed-ingest.manifest:classpath:seed-photos.json}") manifest: Resource,
    ): ApplicationRunner {
        val ingest = IngestSeedHeroPhotos(resolver, fetcher, IngestHeroImage(port))
        return ApplicationRunner { _: ApplicationArguments ->
            val json = StreamUtils.copyToString(manifest.inputStream, Charsets.UTF_8)
            val photos = manifestParser.parse(json)
            val results = ingest.ingestAll(photos)
            results.groupBy { it.status }.forEach { (status, rows) ->
                LOGGER.info("seed image ingest: {} -> {} row(s) {}", status, rows.size, rows.map { it.photoName })
            }
            rowsOf(results, SeedPhotoIngestResult.Status.UNRESOLVED).forEach {
                LOGGER.warn("UNRESOLVED seed image: {} — {}", it.photoName, it.message)
            }
            rowsOf(results, SeedPhotoIngestResult.Status.FETCH_FAILED).forEach {
                LOGGER.warn("FETCH_FAILED seed image: {} — {}", it.photoName, it.message)
            }
            rowsOf(results, SeedPhotoIngestResult.Status.STORE_FAILED).forEach {
                LOGGER.warn("STORE_FAILED seed image: {} — {}", it.photoName, it.message)
            }
        }
    }

    private fun rowsOf(results: List<SeedPhotoIngestResult>, status: SeedPhotoIngestResult.Status): List<SeedPhotoIngestResult> =
        results.filter { it.status == status }

    private companion object {
        private val LOGGER = LoggerFactory.getLogger(SeedImageIngestRunnerConfig::class.java)
    }
}
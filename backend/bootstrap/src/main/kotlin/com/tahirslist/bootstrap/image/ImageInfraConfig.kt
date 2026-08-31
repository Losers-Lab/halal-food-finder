package com.tahirslist.bootstrap.image

import com.tahirslist.application.image.ImageFetcher
import com.tahirslist.application.image.ImageFetchException
import com.tahirslist.application.image.ImagePort
import com.tahirslist.application.image.InMemoryImagePort
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Conditional
import org.springframework.context.annotation.Configuration
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Descriptive User-Agent for outbound image fetches. Wikimedia (and some CDNs)
 * reject clients whose UA is a bare "Java-http-client/..." (their User-Agent
 * policy: require an identifying product/token), returning HTTP 403 for
 * otherwise-valid freely-licensed Commons images. Identifying the app lets those
 * refs (which the manifest image_policy PREFERS) fetch instead of 403ing.
 */
private const val DEFAULT_USER_AGENT =
    "TahirsList/0.1 (halal-food-finder seed-photo ingest; https://github.com/Losers-Lab/halal-food-finder)"

/**
 * Wires the sc-157 image plumbing that lives OUTSIDE the adapters:
 *
 *  - **ImagePort fallback**: when no `app.storage.s3.endpoint` is configured the
 *    S3 adapter produces no bean, so this provides [InMemoryImagePort] to keep
 *    the app bootable for dev/test (PostgresBootTest etc. do not run MinIO).
 *    When S3 IS configured its bean wins (this is `@ConditionalOnMissingBean`).
 *  - **ImageFetcher**: the seed-manifest fetch seam (JDK HttpClient, mirroring
 *    the Photon geocoder) so ingest can reach hero_urls. Internal, server-side.
 */
@Configuration
class ImageInfraConfig {

    @Bean
    @Conditional(NoS3EndpointCondition::class)
    @ConditionalOnMissingBean
    fun imagePort(): ImagePort = InMemoryImagePort()

    @Bean
    fun imageFetcher(): ImageFetcher = HttpImageFetcher()
}

/** JDK HttpClient downloader: 2xx → bytes, anything else → [ImageFetchException]. */
class HttpImageFetcher(
    private val timeout: Duration = Duration.ofSeconds(20),
    private val userAgent: String = DEFAULT_USER_AGENT,
) : ImageFetcher {

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(timeout)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    override fun fetch(url: String): ByteArray {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(timeout)
            .header("Accept", "image/*")
            // Wikimedia (and some CDNs) reject clients whose UA is a bare
            // "Java-http-client/..." (their User-Agent policy). We identify the
            // app so freely-licensed Commons refs (the manifest image_policy
            // PREFERS) are accepted instead of 403ing.
            .header("User-Agent", userAgent)
            .GET()
            .build()
        val response = try {
            client.send(request, HttpResponse.BodyHandlers.ofByteArray())
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw ImageFetchException("interrupted fetching $url", e)
        } catch (e: Exception) {
            throw ImageFetchException("fetch failed for $url: ${e.message}", e)
        }
        if (response.statusCode() !in 200..299) {
            throw ImageFetchException("non-2xx $url: HTTP ${response.statusCode()}")
        }
        return response.body()
    }
}
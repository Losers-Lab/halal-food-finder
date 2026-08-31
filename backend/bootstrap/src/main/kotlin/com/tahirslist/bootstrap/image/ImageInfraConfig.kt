package com.tahirslist.bootstrap.image

import com.tahirslist.application.image.ImageFetcher
import com.tahirslist.application.image.ImageFetchException
import com.tahirslist.application.image.ImagePort
import com.tahirslist.application.image.InMemoryImagePort
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

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
    @ConditionalOnMissingBean
    fun imagePort(): ImagePort = InMemoryImagePort()

    @Bean
    fun imageFetcher(): ImageFetcher = HttpImageFetcher()
}

/** JDK HttpClient downloader: 2xx → bytes, anything else → [ImageFetchException]. */
class HttpImageFetcher(
    private val timeout: Duration = Duration.ofSeconds(20),
) : ImageFetcher {

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(timeout)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    override fun fetch(url: String): ByteArray {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(timeout)
            .header("Accept", "image/*")
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
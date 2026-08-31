package com.tahirslist.verification.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.tahirslist.application.verification.VerificationProviderException
import com.tahirslist.domain.verification.CertificationImage
import com.tahirslist.domain.verification.ModelJudgment
import com.tahirslist.domain.verification.ModelVerdict
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64

/**
 * The default [VisionModelClient]: JSON-over-HTTP to a hosted multimodal model,
 * using only the JDK [HttpClient] (no extra HTTP dependency). [config] supplies
 * the endpoint, model name and optional API key (resolved from a secret source
 * at bootstrap, never baked here).
 *
 * Conservative failure handling:
 *  - non-2xx status -> [VerificationProviderException] (no usable response).
 *  - IO / timeout / interrupt -> [VerificationProviderException] (provider outage).
 *  - a 2xx body with no determinable verdict (not JSON, missing/unknown verdict)
 *    -> [ModelVerdict.INCONCLUSIVE] — never invent a verdict; the caller's
 *    conservative policy defers that to a human.
 */
class RestVisionModelClient(
    private val config: HostedVisionConfig,
    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(config.timeoutMillis))
        .build(),
    private val mapper: ObjectMapper = ObjectMapper(),
) : VisionModelClient {

    override fun analyze(prompt: String, image: CertificationImage): ModelJudgment {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(config.endpoint))
            .timeout(Duration.ofMillis(config.timeoutMillis))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .apply { config.apiKey?.let { header("Authorization", "Bearer $it") } }
            .POST(HttpRequest.BodyPublishers.ofString(requestBody(prompt, image)))
            .build()

        val response = try {
            http.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (e: IOException) {
            throw VerificationProviderException("Vision provider unreachable or timed out: ${e.message}", e)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw VerificationProviderException("Interrupted while calling vision provider", e)
        }

        val code = response.statusCode()
        if (code !in 200..299) {
            throw VerificationProviderException("Vision provider returned HTTP $code")
        }
        return parseJudgment(response.body())
    }

    private fun requestBody(prompt: String, image: CertificationImage): String = mapper.writeValueAsString(
        mapOf(
            "model" to config.modelName,
            "prompt" to prompt,
            "image_base64" to Base64.getEncoder().encodeToString(image.bytes),
        )
    )

    private fun parseJudgment(body: String): ModelJudgment {
        val node = try {
            mapper.readTree(body)
        } catch (e: IOException) {
            return ModelJudgment(ModelVerdict.INCONCLUSIVE, 0.0, "unparseable provider response")
        }
        val verdictName = node.path("verdict").asText("").trim()
        val verdict = runCatching { ModelVerdict.valueOf(verdictName) }
            .getOrElse { ModelVerdict.INCONCLUSIVE }
        val confidence = node.path("confidence").asDouble(0.0)
        val summary = node.path("summary").asText("").takeIf { it.isNotBlank() }
        return ModelJudgment(verdict, confidence, summary)
    }
}
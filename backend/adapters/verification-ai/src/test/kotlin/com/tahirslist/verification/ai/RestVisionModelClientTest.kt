package com.tahirslist.verification.ai

import com.sun.net.httpserver.HttpServer
import com.tahirslist.application.verification.VerificationProviderException
import com.tahirslist.domain.verification.CertificationImage
import com.tahirslist.domain.verification.ModelVerdict
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.time.Duration
import java.util.Base64

/**
 * RestVisionModelClient is the JSON-over-HTTP transport behind the default
 * HostedVisionAdapter. It is exercised against a pure-JDK HttpServer on a loopback
 * port — NO real provider and NO live network. This proves the transport maps the
 * hosted provider's JSON into a domain ModelJudgment, and that provider outages /
 * malformed output are handled conservatively.
 */
class RestVisionModelClientTest : FunSpec({

    var status: Int = 200
    var body: String = ""
    var delayMillis: Long = 0
    var lastBody: String? = null

    lateinit var baseUrl: String
    lateinit var server: HttpServer

    beforeSpec {
        server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/") { exchange ->
            lastBody = exchange.requestBody.bufferedReader().readText()
            if (delayMillis > 0) Thread.sleep(delayMillis)
            val payload = body.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, payload.size.toLong())
            exchange.responseBody.use { it.write(payload) }
        }
        server.start()
        baseUrl = "http://127.0.0.1:${server.address.port}/"
    }
    afterSpec { server.stop(0) }

    fun setResponse(statusCode: Int, responseBody: String) {
        status = statusCode
        body = responseBody
        delayMillis = 0
    }

    fun client(timeout: Duration = Duration.ofSeconds(5)) = RestVisionModelClient(
        config = HostedVisionConfig(endpoint = baseUrl, modelName = "gemini-2.5-flash", timeoutMillis = timeout.toMillis()),
    )

    val image = CertificationImage("image/jpeg", byteArrayOf(9, 8, 7))

    fun payload(verdict: String, confidence: Double, summary: String = "") =
        """{"verdict":"$verdict","confidence":$confidence,"summary":"$summary"}"""

    test("maps a CERT_VALID JSON verdict to a ModelJudgment") {
        setResponse(200, payload("CERT_VALID", 0.95, "matches listed restaurant"))

        val result = client().analyze("ignored", image)

        result.verdict shouldBe ModelVerdict.CERT_VALID
        result.confidence shouldBe 0.95
        result.summary shouldBe "matches listed restaurant"
    }

    test("maps a NOT_VALID JSON verdict to a ModelJudgment") {
        setResponse(200, payload("NOT_VALID", 0.9))

        client().analyze("ignored", image).verdict shouldBe ModelVerdict.NOT_VALID
    }

    test("sends the cert image (base64) and prompt to the provider endpoint") {
        setResponse(200, payload("INCONCLUSIVE", 0.1))

        client().analyze("analyze this cert", image)

        lastBody shouldNotBe null
        lastBody!!.shouldContain(Base64.getEncoder().encodeToString(byteArrayOf(9, 8, 7)))
        lastBody!!.shouldContain("analyze this cert")
    }

    test("an unknown verdict string is mapped conservatively to INCONCLUSIVE") {
        setResponse(200, payload("MAYBE_HALAL", 0.9))

        val result = client().analyze("ignored", image)

        result.verdict shouldBe ModelVerdict.INCONCLUSIVE
    }

    test("a 2xx body that is not JSON at all is treated conservatively as INCONCLUSIVE") {
        setResponse(200, "not json at all")

        client().analyze("ignored", image).verdict shouldBe ModelVerdict.INCONCLUSIVE
    }

    test("a non-2xx response is surfaced as a VerificationProviderException") {
        setResponse(429, payload("CERT_VALID", 0.9))

        shouldThrow<VerificationProviderException> { client().analyze("ignored", image) }
    }

    test("a connect failure is surfaced as a VerificationProviderException (not leaked)") {
        val refusedPort = ServerSocket(0).use { it.localPort }
        val dead = RestVisionModelClient(
            config = HostedVisionConfig(endpoint = "http://127.0.0.1:$refusedPort/", timeoutMillis = 2000),
        )

        shouldThrow<VerificationProviderException> { dead.analyze("ignored", image) }
    }

    test("a slow provider times out and surfaces as a VerificationProviderException") {
        setResponse(200, payload("CERT_VALID", 0.9))
        delayMillis = 2000

        shouldThrow<VerificationProviderException> {
            client(timeout = Duration.ofMillis(200)).analyze("ignored", image)
        }
    }
})
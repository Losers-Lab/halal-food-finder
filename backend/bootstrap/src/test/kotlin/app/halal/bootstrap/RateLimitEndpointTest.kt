package app.halal.bootstrap

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.ClientHttpResponse
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.test.context.TestPropertySource
import org.springframework.web.client.ResponseErrorHandler
import org.springframework.web.client.RestTemplate

/**
 * sc-136 rate limiting (end-to-end). A deliberately tiny, per-class token bucket
 * (`app.ratelimit.capacity=2`) is injected so the 429s are asserted deterministically
 * against the real HTTP stack — proving the filter short-circuits before the auth
 * use case runs and returns the generic, non-informative envelope. Budgets are
 * per in-process client IP + route.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = [
        "app.ratelimit.capacity=2",
        "app.ratelimit.refill-per-window=2",
        "app.ratelimit.refill-window=1m",
        // XFF trust is opt-in since the sc-136 review fix; these tests simulate
        // distinct client IPs via X-Forwarded-For, so the proxy trust is enabled
        // here explicitly (the default remoteAddr-only path is covered by unit
        // tests in web-api and by the two tests above, which share one socket peer).
        "app.ratelimit.trust-proxy=true",
    ],
)
class RateLimitEndpointTest : PostgresBootTest() {

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    // Cookie management disabled so the test controls exactly which cookies are presented.
    @LocalServerPort
    var port: Int = 0

    private val client: RestTemplate by lazy {
        val httpClient: CloseableHttpClient = HttpClients.custom().disableCookieManagement().build()
        RestTemplate().apply {
            requestFactory = HttpComponentsClientHttpRequestFactory(httpClient)
            setErrorHandler(object : ResponseErrorHandler {
                override fun hasError(response: ClientHttpResponse): Boolean = false
                override fun handleError(response: ClientHttpResponse) { /* return body as-is */ }
            })
        }
    }

    private fun url(path: String) = "http://localhost:$port$path"

    init {
        test("login rate-limit kicks in after the burst capacity is exhausted (429, generic envelope)") {
            // First two login attempts fill the tiny (capacity=2) bucket.
            repeat(2) {
                client.postForEntity(url("/v1/auth/login"), loginBody("cap@example.com", "wrong"), Map::class.java)
                    .statusCode.shouldBe(HttpStatus.UNAUTHORIZED)
            }

            // Third attempt from the same client+route is rate-limited.
            val throttled = client.postForEntity(url("/v1/auth/login"), loginBody("cap@example.com", "wrong"), Map::class.java)
            throttled.statusCode shouldBe HttpStatus.TOO_MANY_REQUESTS
            throttled.body!!["code"] shouldBe "rate_limited"
            throttled.body!!["message"].toString().isNotBlank() shouldBe true
        }

        test("refresh rate-limit kicks in after its own burst capacity (separate route bucket)") {
            // The refresh route has its own bucket, so login starvation (above) does not
            // bleed into it: these two refresh attempts pass the filter (401 from auth,
            // not 429 from the limiter).
            repeat(2) {
                client.exchange(
                    url("/v1/auth/refresh"),
                    HttpMethod.POST,
                    cookieEntity("never-issued-token"),
                    Map::class.java,
                ).statusCode.shouldBe(HttpStatus.UNAUTHORIZED)
            }

            val throttled = client.exchange(
                url("/v1/auth/refresh"),
                HttpMethod.POST,
                cookieEntity("never-issued-token"),
                Map::class.java,
            )
            throttled.statusCode shouldBe HttpStatus.TOO_MANY_REQUESTS
            throttled.body!!["code"] shouldBe "rate_limited"
        }

        test("a different client IP is not throttled by another's exhaustion") {
            // Two different X-Forwarded-For values get independent buckets.
            val first = client.exchange(
                url("/v1/auth/refresh"),
                HttpMethod.POST,
                cookieEntity("tok", forwardedFor = "203.0.113.7"),
                Map::class.java,
            )
            val second = client.exchange(
                url("/v1/auth/refresh"),
                HttpMethod.POST,
                cookieEntity("tok", forwardedFor = "203.0.113.8"),
                Map::class.java,
            )
            // 401 (auth) for both — neither was rate-limited despite the cap.
            first.statusCode shouldBe HttpStatus.UNAUTHORIZED
            second.statusCode shouldBe HttpStatus.UNAUTHORIZED
        }
    }

    private fun loginBody(email: String, password: String): HttpEntity<Map<String, String>> {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        return HttpEntity(mapOf("email" to email, "password" to password), headers)
    }

    private fun cookieEntity(refreshToken: String, forwardedFor: String? = null): HttpEntity<Any?> {
        val headers = HttpHeaders()
        headers.add(HttpHeaders.COOKIE, "refresh_token=$refreshToken")
        if (forwardedFor != null) headers.add("X-Forwarded-For", forwardedFor)
        return HttpEntity<Any?>(null, headers)
    }
}
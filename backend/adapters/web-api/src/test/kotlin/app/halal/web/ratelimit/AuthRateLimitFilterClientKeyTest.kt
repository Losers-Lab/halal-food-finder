package app.halal.web.ratelimit

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

/**
 * sc-136 review fix: the rate-limit key must not be client-spoofable via the
 * X-Forwarded-For header. By default (no trusted proxy configured) the key
 * derives from the socket peer (remoteAddr) only; trusting XFF is opt-in.
 */
class AuthRateLimitFilterClientKeyTest : FunSpec({

    val mapper = ObjectMapper()

    fun request(
        remoteAddr: String = "203.0.113.9",
        requestUri: String = "/v1/auth/login",
        forwardedFor: String? = null,
    ): jakarta.servlet.http.HttpServletRequest {
        val req = mockk<jakarta.servlet.http.HttpServletRequest>()
        every { req.method } returns "POST"
        every { req.requestURI } returns requestUri
        every { req.remoteAddr } returns remoteAddr
        every { req.getHeader("X-Forwarded-For") } returns forwardedFor
        return req
    }

    test("spoofed X-Forwarded-For is ignored by default (remoteAddr keys the bucket)") {
        val filter = AuthRateLimitFilter(NoopRateLimiter, mapper, trustProxy = false)
        val req = request(forwardedFor = "1.2.3.4, 10.0.0.1")

        filter.clientKey(req) shouldBe "/v1/auth/login|203.0.113.9"
    }

    test("without XFF the immediate peer keys the bucket") {
        val filter = AuthRateLimitFilter(NoopRateLimiter, mapper, trustProxy = false)
        val req = request(forwardedFor = null)

        filter.clientKey(req) shouldBe "/v1/auth/login|203.0.113.9"
    }

    test("with trust-proxy enabled the first XFF hop keys the bucket") {
        val filter = AuthRateLimitFilter(NoopRateLimiter, mapper, trustProxy = true)
        val req = request(forwardedFor = "1.2.3.4, 10.0.0.1")

        filter.clientKey(req) shouldBe "/v1/auth/login|1.2.3.4"
    }

    test("with trust-proxy enabled a blank XFF falls back to remoteAddr") {
        val filter = AuthRateLimitFilter(NoopRateLimiter, mapper, trustProxy = true)
        val req = request(forwardedFor = "   ")

        filter.clientKey(req) shouldBe "/v1/auth/login|203.0.113.9"
    }
})

/** Test double that always admits, so clientKey tests never hit bucket logic. */
private object NoopRateLimiter : AuthRateLimiter {
    override fun tryAcquire(key: String): Boolean = true
}

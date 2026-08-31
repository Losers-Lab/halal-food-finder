package com.tahirslist.web.ratelimit

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.filter.OncePerRequestFilter
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.IOException

/**
 * sc-136 auth rate limiting: an HTTP-level token-bucket gate in front of the
 * login and refresh endpoints, keyed by client IP. Exceeding the configured
 * budget returns HTTP 429 with a generic, non-informative envelope —
 * deliberately conservative and imitation-free: no body echo, no retry hints,
 * no IP enumeration.
 *
 * Placement: as a plain servlet filter registered BEFORE Spring Security so it
 * short-circuits even auth attempts (deny-by-default posture). Budgets are per
 * in-process client IP; see [Bucket4jAuthRateLimiter] for the single-node caveat.
 */
@Configuration
@EnableConfigurationProperties(AuthRateLimitProperties::class)
class AuthRateLimitConfig {

    @Bean
    fun authRateLimiter(
        authRateLimitProperties: AuthRateLimitProperties,
    ): Bucket4jAuthRateLimiter = Bucket4jAuthRateLimiter(
        capacity = authRateLimitProperties.capacity,
        refillTokensPerWindow = authRateLimitProperties.refillPerWindow,
        refillWindow = authRateLimitProperties.refillWindow,
        maxKeys = authRateLimitProperties.maxKeys,
    )

    @Bean
    fun authRateLimitFilter(
        authRateLimiter: AuthRateLimiter,
        objectMapper: ObjectMapper,
        authRateLimitProperties: AuthRateLimitProperties,
    ): FilterRegistrationBean<AuthRateLimitFilter> {
        val filter = AuthRateLimitFilter(authRateLimiter, objectMapper, authRateLimitProperties.trustProxy)
        val registration = FilterRegistrationBean(filter)
        // Run before Spring Security's filter chain so rate-limited requests are
        // refused before any auth processing (deny-by-default, cheap reject).
        registration.order = Ordered.HIGHEST_PRECEDENCE + 10
        registration.addUrlPatterns("/v1/auth/login", "/v1/auth/refresh")
        return registration
    }
}

/**
 * Rejects requests that have exhausted their token bucket. Only the HTTP methods
 * these auth routes accept are counted (POST); a 429 stops the request.
 */
class AuthRateLimitFilter(
    private val limiter: AuthRateLimiter,
    private val objectMapper: ObjectMapper,
    /**
     * Opt-in trust for the client-supplied X-Forwarded-For header. Enable ONLY
     * behind a proxy that overwrites XFF; leaving it false keeps the key
     * unspoofable by clients.
     */
    private val trustProxy: Boolean = false,
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(AuthRateLimitFilter::class.java)

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        !"POST".equals(request.method, ignoreCase = true)

    @Throws(IOException::class, ServletException::class)
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val key = clientKey(request)
        if (limiter.tryAcquire(key)) {
            filterChain.doFilter(request, response)
        } else {
            deny(response)
        }
    }

    /**
     * The rate-limit budget key: route + client IP so login and refresh never
     * starve each other. X-Forwarded-For is client-controlled and is trusted
     * only when [trustProxy] is explicitly enabled (app.ratelimit.trust-proxy);
     * otherwise the socket peer (remoteAddr) keys the bucket, so clients cannot
     * rotate their budget by forging headers.
     */
    internal fun clientKey(request: HttpServletRequest): String {
        val ip = if (trustProxy) {
            request.getHeader("X-Forwarded-For")?.substringBefore(",")?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: request.remoteAddr
                ?: "unknown"
        } else {
            request.remoteAddr ?: "unknown"
        }
        val route = request.requestURI
        return "$route|$ip"
    }

    private fun deny(response: HttpServletResponse) {
        log.debug("Auth request rate-limited")
        response.status = HttpStatus.TOO_MANY_REQUESTS.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        objectMapper.writeValue(
            response.writer,
            mapOf("code" to "rate_limited", "message" to "Too many requests. Please try again later."),
        )
    }
}
package app.halal.web.ratelimit

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Configurable budgets for the sc-136 auth rate limiter, bound from
 * `app.ratelimit.*`. Single node, in-process (see [Bucket4jAuthRateLimiter]).
 */
@ConfigurationProperties(prefix = "app.ratelimit")
data class AuthRateLimitProperties(
    /** Token-bucket capacity (max immediate requests before the budget empties). */
    var capacity: Long = 20,
    /** Tokens refilled per [refillWindow] (steady-state sustained rate). */
    var refillPerWindow: Long = 20,
    /** The refill window for [refillPerWindow]. */
    var refillWindow: Duration = Duration.ofMinutes(1),
    /**
     * Trust the client-supplied X-Forwarded-For header when deriving the
     * rate-limit key. Enable ONLY when the app runs behind a proxy that
     * overwrites (not appends to) XFF; otherwise the key is client-spoofable.
     * Default false: the immediate socket peer (remoteAddr) keys the bucket.
     */
    var trustProxy: Boolean = false,
    /** Upper bound on tracked client keys (memory-growth guard; LRU-evicted). */
    var maxKeys: Long = 100_000,
)
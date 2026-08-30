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
)
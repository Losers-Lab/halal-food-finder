package app.halal.web.ratelimit

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import io.github.bucket4j.Refill
import io.github.bucket4j.TimeMeter
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * In-process token-bucket rate limiter for auth endpoints (sc-136), keyed by a
 * caller-supplied key (typically client IP + route). Backed by Bucket4j — the
 * in-app limiter recommended for MVP scale by the security review (finding #4 /
 * Part 3 #5). It is $0, needs no external service, and lives entirely in the
 * process.
 *
 * Single-node caveat: buckets are in-memory, so the budget belongs to one
 * process and resets on restart. That is the accepted conservative posture for a
 * single-node MVP; a horizontally-scaled deployment needs a shared counter
 * (e.g. Redis) — explicitly out of scope here.
 *
 * A [TimeMeter] is injectable so tests can advance time deterministically; the
 * default is Bucket4j's built-in system clock.
 */
class Bucket4jAuthRateLimiter(
    capacity: Long,
    refillTokensPerWindow: Long,
    refillWindow: Duration,
    private val meter: TimeMeter = TimeMeter.SYSTEM_NANOTIME,
) : AuthRateLimiter {

    private val refill = Refill.greedy(refillTokensPerWindow, refillWindow)
    private val limit = Bandwidth.classic(capacity, refill)
    private val buckets = ConcurrentHashMap<String, Bucket>()

    override fun tryAcquire(key: String): Boolean =
        buckets.computeIfAbsent(key) { newBucket() }.tryConsume(1)

    private fun newBucket(): Bucket = Bucket.builder().withCustomTimePrecision(meter).addLimit(limit).build()
}

/**
 * Port for the in-process auth rate limiter (kept framework-free so the web
 * adapter owns the seam without leaking Bucket4j into the use cases).
 */
interface AuthRateLimiter {
    /** Returns `true` if a token was available (request allowed), `false` if rate-limited. */
    fun tryAcquire(key: String): Boolean
}
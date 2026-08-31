package com.tahirslist.web.ratelimit

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import io.github.bucket4j.Refill
import io.github.bucket4j.TimeMeter
import java.time.Duration
import java.util.Collections
import java.util.LinkedHashMap

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
    /**
     * Upper bound on tracked keys. Keys derive from client-controllable input,
     * so an unbounded map would be a memory-growth DoS vector; beyond this
     * bound the least-recently-used bucket is evicted (an evicted client
     * simply restarts with a fresh budget — the conservative direction).
     */
    val maxKeys: Long = 100_000L,
) : AuthRateLimiter {

    init {
        require(maxKeys > 0) { "maxKeys must be positive" }
    }

    private val refill = Refill.greedy(refillTokensPerWindow, refillWindow)
    private val limit = Bandwidth.classic(capacity, refill)

    // Access-order LinkedHashMap behind a synchronized wrapper: bounded LRU
    // keyed eviction. Small map, low traffic on auth routes — lock contention
    // is negligible at this scale.
    private val buckets: MutableMap<String, Bucket> = Collections.synchronizedMap(
        object : LinkedHashMap<String, Bucket>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bucket>): Boolean =
                size > maxKeys
        },
    )

    /** Current number of tracked client buckets (exposed for tests/ops). */
    internal val trackedKeyCount: Int get() = buckets.size

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
package app.halal.web.ratelimit

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Duration

/**
 * In-process rate limiter (sc-136) tests, using a controllable [TestTimeMeter]
 * so burst-capacity, steady-state refill, and per-key isolation are asserted
 * deterministically (no wall-clock sleeps).
 */
class Bucket4jAuthRateLimiterTest : FunSpec({

    test("allows up to the bucket capacity in a burst, then refuses") {
        val limiter = Bucket4jAuthRateLimiter(
            capacity = 5,
            refillTokensPerWindow = 5,
            refillWindow = Duration.ofMinutes(1),
        )

        (1..5).forEach { limiter.tryAcquire("ip-a") shouldBe true }
        limiter.tryAcquire("ip-a") shouldBe false
    }

    test("isolates budgets per key: one client's exhaustion does not starve another") {
        val limiter = Bucket4jAuthRateLimiter(
            capacity = 2,
            refillTokensPerWindow = 2,
            refillWindow = Duration.ofMinutes(1),
        )

        limiter.tryAcquire("ip-a") shouldBe true
        limiter.tryAcquire("ip-a") shouldBe true
        limiter.tryAcquire("ip-a") shouldBe false

        // A different client still has its own full bucket.
        limiter.tryAcquire("ip-b") shouldBe true
        limiter.tryAcquire("ip-b") shouldBe true
    }

    test("refills tokens over the window (sustained steady-state rate is honoured)") {
        val window = Duration.ofSeconds(60)
        val meter = TestTimeMeter()
        val limiter = Bucket4jAuthRateLimiter(
            capacity = 2,
            refillTokensPerWindow = 2,
            refillWindow = window,
            meter = meter,
        )

        // Empty the bucket.
        limiter.tryAcquire("ip-c") shouldBe true
        limiter.tryAcquire("ip-c") shouldBe true
        limiter.tryAcquire("ip-c") shouldBe false

        // Advance one full window: the greedy refill restores the capacity.
        meter.advance(window)
        limiter.tryAcquire("ip-c") shouldBe true
    }

    test("a partially-elapsed window refills proportionally and still admits one more") {
        val window = Duration.ofSeconds(60)
        val meter = TestTimeMeter()
        val limiter = Bucket4jAuthRateLimiter(
            capacity = 2,
            refillTokensPerWindow = 4,
            refillWindow = window,
            meter = meter,
        )

        limiter.tryAcquire("ip-d") shouldBe true
        limiter.tryAcquire("ip-d") shouldBe true
        limiter.tryAcquire("ip-d") shouldBe false

        // Half the window elapses => greedy refill has partially replenished.
        meter.advance(window.dividedBy(2))
        limiter.tryAcquire("ip-d") shouldBe true
    }

    test("the bucket map is bounded: keys are evicted beyond maxKeys (memory-growth guard)") {
        val limiter = Bucket4jAuthRateLimiter(
            capacity = 5,
            refillTokensPerWindow = 5,
            refillWindow = Duration.ofMinutes(1),
            maxKeys = 3,
        )

        // Touch more distinct keys than the bound allows.
        listOf("ip-1", "ip-2", "ip-3", "ip-4", "ip-5").forEach { limiter.tryAcquire(it) shouldBe true }

        limiter.trackedKeyCount shouldBe 3
    }

    test("the default bound is finite (no unbounded growth possible)") {
        val limiter = Bucket4jAuthRateLimiter(
            capacity = 5,
            refillTokensPerWindow = 5,
            refillWindow = Duration.ofMinutes(1),
        )

        (1..2000).forEach { limiter.tryAcquire("client-$it") }

        limiter.maxKeys shouldBe 100_000L
        (limiter.trackedKeyCount <= limiter.maxKeys) shouldBe true
    }
})

/** [io.github.bucket4j.TimeMeter] whose time the test controls, making refill deterministic. */
class TestTimeMeter : io.github.bucket4j.TimeMeter {
    private var nanos = 0L
    override fun currentTimeNanos(): Long = nanos
    override fun isWallClockBased(): Boolean = false
    fun advance(duration: Duration) {
        nanos += duration.toNanos()
    }
}

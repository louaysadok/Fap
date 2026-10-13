package com.fap.core

/**
 * Politeness controls. Scraping a media site at full speed gets you blocked and
 * is rude, so every request goes through here.
 *
 * Pure Kotlin: the sleeper and clock are injected so tests run instantly.
 */
public fun interface Sleeper {
    public fun sleep(millis: Long)
}

public fun interface Clock {
    public fun now(): Long
}

public class RateLimiter(
    /** Requests per second, sustained. */
    public var requestsPerSecond: Double = 2.0,
    /** Extra random-ish pause in milliseconds added to every call. */
    public var jitterMs: LongRange = 120L..320L,
    private val sleeper: Sleeper = Sleeper { Thread.sleep(it) },
    private val clock: Clock = Clock { System.currentTimeMillis() },
    private val random: kotlin.random.Random = kotlin.random.Random.Default
) {
    private var tokens: Double = 1.0
    private var lastRefill: Long = -1L

    @Synchronized
    public fun acquire() {
        val now = clock.now()
        if (lastRefill < 0) lastRefill = now
        val elapsed = (now - lastRefill).coerceAtLeast(0)
        tokens = (tokens + elapsed / 1000.0 * requestsPerSecond).coerceAtMost(1.0)
        lastRefill = now
        if (tokens < 1.0) {
            val wait = ((1.0 - tokens) / requestsPerSecond * 1000.0).toLong().coerceAtLeast(0)
            if (wait > 0) {
                sleeper.sleep(wait)
                tokens = 0.0
                lastRefill = clock.now()
            } else {
                tokens -= 1.0
            }
        } else {
            tokens -= 1.0
        }
        val jitter = if (jitterMs.last > jitterMs.first) {
            jitterMs.first + random.nextLong(jitterMs.last - jitterMs.first)
        } else jitterMs.first
        if (jitter > 0) sleeper.sleep(jitter)
    }
}

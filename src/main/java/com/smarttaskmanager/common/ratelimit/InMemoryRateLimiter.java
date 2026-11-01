package com.smarttaskmanager.common.ratelimit;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight, thread-safe in-memory rate limiter using a sliding-window algorithm.
 *
 * <p>Tracks request timestamps per key in a concurrent map. Suitable for single-instance
 * deployments without external Redis dependencies.</p>
 *
 * <p><b>Limitation Note:</b> State is kept in JVM memory and does not survive application
 * restarts or synchronize across multiple instances.</p>
 */
@Component
public class InMemoryRateLimiter {

    private final ConcurrentHashMap<String, Deque<Instant>> requestLog = new ConcurrentHashMap<>();

    /**
     * Checks if a request is permitted under the rate limit, recording the attempt if allowed.
     *
     * @param key unique identifier (e.g., action + email or action + IP)
     * @param maxRequests maximum allowed requests within the duration window
     * @param window sliding window duration
     * @return true if allowed, false if limit exceeded
     */
    public boolean tryAcquire(String key, int maxRequests, Duration window) {
        Instant now = Instant.now();
        Instant windowStart = now.minus(window);

        return requestLog.compute(key, (k, timestamps) -> {
            if (timestamps == null) {
                timestamps = new ArrayDeque<>();
            }

            // Evict timestamps older than the sliding window
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(windowStart)) {
                timestamps.pollFirst();
            }

            if (timestamps.size() < maxRequests) {
                timestamps.addLast(now);
                return timestamps;
            } else {
                return timestamps;
            }
        }).size() <= maxRequests && requestLog.get(key).peekLast() != null && requestLog.get(key).peekLast().equals(now);
    }

    /**
     * Resets rate-limiting history for a specific key (useful for test resets).
     */
    public void reset(String key) {
        requestLog.remove(key);
    }

    /**
     * Clears all rate limiter state.
     */
    public void clearAll() {
        requestLog.clear();
    }
}

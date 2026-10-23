package com.supportiq.service.chat;

import com.supportiq.exception.ChatRateLimitException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sliding window rate limiter for customer chat requests.
 * Default limit: 20 requests per minute per user (configurable via app.rag.chat-rate-limit-per-minute).
 */
@Component
public class ChatRateLimiter {

    private final int limitPerMinute;
    private final ConcurrentHashMap<String, Deque<Long>> userRequestTimestamps = new ConcurrentHashMap<>();

    public ChatRateLimiter(@Value("${app.rag.chat-rate-limit-per-minute:20}") int limitPerMinute) {
        this.limitPerMinute = limitPerMinute > 0 ? limitPerMinute : 20;
    }

    /**
     * Checks if a request from the given user is permitted within the sliding 1-minute window.
     * Throws {@link ChatRateLimitException} if the limit is exceeded.
     *
     * @param userKey unique identifier for the user (e.g. user id or email)
     */
    public void checkRateLimit(String userKey) {
        if (userKey == null || userKey.isBlank()) {
            return;
        }

        long now = System.currentTimeMillis();
        long windowStart = now - 60_000L;

        // Atomically update sliding window timestamps for the user
        userRequestTimestamps.compute(userKey, (k, timestamps) -> {
            if (timestamps == null) {
                timestamps = new ArrayDeque<>();
            }

            // Evict expired entries outside the 60-second sliding window
            while (!timestamps.isEmpty() && timestamps.peekFirst() <= windowStart) {
                timestamps.pollFirst();
            }

            if (timestamps.size() >= limitPerMinute) {
                throw new ChatRateLimitException("Too many chat requests. Please wait a moment before sending another message.");
            }

            timestamps.addLast(now);
            return timestamps;
        });
    }

    /**
     * Clears all recorded timestamps. Useful for testing.
     */
    public void reset() {
        userRequestTimestamps.clear();
    }

    public int getLimitPerMinute() {
        return limitPerMinute;
    }
}

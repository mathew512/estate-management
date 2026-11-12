package com.kasa.estate_management.security;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Allows at most {@code maxEvents} events per key inside a sliding window. Taking a slot and
 * checking the limit happen together under one lock, so parallel requests cannot all slip through.
 * State is in memory: per server, and cleared by a restart.
 */
public class SlidingWindowLimiter {

    public record Decision(boolean allowed, long retryAfterSeconds, boolean firstBlock) {}

    private static final class Bucket {
        final ArrayDeque<Instant> events = new ArrayDeque<>();
        boolean blockAnnounced;
    }

    private final int maxEvents;
    private final Duration window;
    private final int maxKeys;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public SlidingWindowLimiter(int maxEvents, Duration window, int maxKeys) {
        this.maxEvents = Math.max(1, maxEvents);
        this.window = window;
        this.maxKeys = maxKeys;
    }

    /** Takes a slot if one is free. Otherwise reports how long to wait and whether this is the first refusal. */
    public Decision tryAcquire(String key, Instant now) {
        Bucket bucket = buckets.get(key);
        if (bucket == null) {
            if (buckets.size() >= maxKeys) {
                // Table full (someone is spraying keys): stop tracking new ones rather than run out of memory.
                return new Decision(true, 0, false);
            }
            bucket = buckets.computeIfAbsent(key, k -> new Bucket());
        }

        synchronized (bucket) {
            prune(bucket, now);

            if (bucket.events.size() >= maxEvents) {
                long wait = Math.max(1,
                    Duration.between(now, bucket.events.peekFirst().plus(window)).toSeconds() + 1);
                boolean first = !bucket.blockAnnounced;
                bucket.blockAnnounced = true;
                return new Decision(false, wait, first);
            }

            bucket.blockAnnounced = false;
            bucket.events.addLast(now);
            return new Decision(true, 0, false);
        }
    }

    /** Gives back the most recent slot, for a request that turned out not to count. */
    public void releaseLast(String key) {
        Bucket bucket = buckets.get(key);
        if (bucket == null) {
            return;
        }
        synchronized (bucket) {
            bucket.events.pollLast();
        }
    }

    public void clear(String key) {
        buckets.remove(key);
    }

    /** Drops keys whose events have all expired, so the table cannot grow forever. */
    public void cleanup(Instant now) {
        buckets.forEach((key, bucket) -> {
            synchronized (bucket) {
                prune(bucket, now);
                if (bucket.events.isEmpty()) {
                    buckets.remove(key, bucket);
                }
            }
        });
    }

    private void prune(Bucket bucket, Instant now) {
        Instant cutoff = now.minus(window);
        while (!bucket.events.isEmpty() && !bucket.events.peekFirst().isAfter(cutoff)) {
            bucket.events.pollFirst();
        }
    }
}
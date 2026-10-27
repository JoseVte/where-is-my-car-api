package io.josrom.whereismycar.component;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.josrom.whereismycar.exception.TooManyRequestsException;

/**
 * Limits registrations per client IP (token bucket, in memory). Registration is the only public endpoint
 * that creates data and it reveals whether an email exists, so it must not be callable without limit.
 *
 * In memory means one limit per instance: with several instances behind a load balancer, move the
 * buckets to a shared store (Bucket4j supports Redis and JDBC).
 */
@Component
public class RegistrationRateLimiter {
    private final int capacity;
    private final Duration period;
    private final Cache<String, Bucket> buckets;

    public RegistrationRateLimiter(@Value("${app.rate-limit.register.capacity:5}") int capacity,
                                   @Value("${app.rate-limit.register.period:PT1H}") Duration period) {
        this.capacity = capacity;
        this.period = period;
        // Idle buckets are full again after one period, so they can be dropped
        this.buckets = Caffeine.newBuilder().expireAfterAccess(period).maximumSize(100_000).build();
    }

    /** Consumes one registration for the client, or throws if the client used them all. */
    public void consume(String clientIp) {
        Bucket bucket = buckets.get(clientIp, ip -> Bucket.builder()
                .addLimit(limit -> limit.capacity(capacity).refillGreedy(capacity, period))
                .build());

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            throw new TooManyRequestsException("error.rate_limit.register",
                    Duration.ofNanos(probe.getNanosToWaitForRefill()));
        }
    }

    /** Forgets every client. Used by tests. */
    public void reset() {
        buckets.invalidateAll();
    }
}

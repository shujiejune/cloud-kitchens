package org.example.procurementservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;

/**
 * Per-vendor Semaphore token buckets enforcing the published vendor rate limits:
 *
 * <ul>
 *   <li>Amazon  — 1 req/sec per credential set (PA-API v5).</li>
 *   <li>Walmart — 5 req/sec (Open API).</li>
 *   <li>Kroger  — 3 req/sec (burst-sensitive).</li>
 * </ul>
 *
 * Each vendor call acquires a permit; the permit is released 1 second later
 * on {@link #rateLimiterScheduler()}, yielding a rolling 1-second window.
 *
 * Callers should acquire with a timeout so a saturated vendor fails fast
 * rather than stalling the whole plan generation.
 */
@Configuration
public class VendorRateLimiterConfig {

    @Bean(name = "amazonRateLimiter")
    public Semaphore amazonRateLimiter() {
        return new Semaphore(1, true);
    }

    @Bean(name = "walmartRateLimiter")
    public Semaphore walmartRateLimiter() {
        return new Semaphore(5, true);
    }

    @Bean(name = "krogerRateLimiter")
    public Semaphore krogerRateLimiter() {
        return new Semaphore(3, true);
    }

    /** Single daemon thread used to release semaphore permits on a delay. */
    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService rateLimiterScheduler() {
        return Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "vendor-rate-limiter-scheduler");
            t.setDaemon(true);
            return t;
        });
    }
}

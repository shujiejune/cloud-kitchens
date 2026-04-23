package org.example.procurementservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Worker pools for parallel vendor I/O.
 *
 * One worker per vendor is enough — inside a worker, calls are serialized
 * by that vendor's rate limiter, so additional threads would only queue
 * behind the semaphore.
 */
@Configuration
public class ExecutorsConfig {

    /** Used by PriceAggregator to fan out price fetches across vendors. */
    @Bean(name = "priceAggregationExecutor", destroyMethod = "shutdown")
    public ExecutorService priceAggregationExecutor() {
        return Executors.newFixedThreadPool(3, namedDaemon("price-aggregator-"));
    }

    /** Used by VendorOrderFanOutService to fan out order submissions. */
    @Bean(name = "vendorFanOutExecutor", destroyMethod = "shutdown")
    public ExecutorService vendorFanOutExecutor() {
        return Executors.newFixedThreadPool(3, namedDaemon("vendor-fanout-"));
    }

    private static java.util.concurrent.ThreadFactory namedDaemon(String prefix) {
        return new java.util.concurrent.ThreadFactory() {
            private final java.util.concurrent.atomic.AtomicInteger idx = new java.util.concurrent.atomic.AtomicInteger();
            @Override public Thread newThread(Runnable r) {
                Thread t = new Thread(r, prefix + idx.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        };
    }
}

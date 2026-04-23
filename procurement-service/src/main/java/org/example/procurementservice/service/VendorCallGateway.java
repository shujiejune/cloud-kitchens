package org.example.procurementservice.service;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.example.procurementservice.client.VendorApiClients.AmazonVendorClient;
import org.example.procurementservice.client.VendorApiClients.AmazonVendorClient.AmazonOrderRequest;
import org.example.procurementservice.client.VendorApiClients.AmazonVendorClient.AmazonOrderResponse;
import org.example.procurementservice.client.VendorApiClients.AmazonVendorClient.AmazonSearchRequest;
import org.example.procurementservice.client.VendorApiClients.AmazonVendorClient.AmazonSearchResponse;
import org.example.procurementservice.client.VendorApiClients.KrogerVendorClient;
import org.example.procurementservice.client.VendorApiClients.KrogerVendorClient.KrogerOrderRequest;
import org.example.procurementservice.client.VendorApiClients.KrogerVendorClient.KrogerOrderResponse;
import org.example.procurementservice.client.VendorApiClients.KrogerVendorClient.KrogerSearchRequest;
import org.example.procurementservice.client.VendorApiClients.KrogerVendorClient.KrogerSearchResponse;
import org.example.procurementservice.client.VendorApiClients.WalmartVendorClient;
import org.example.procurementservice.client.VendorApiClients.WalmartVendorClient.WalmartOrderRequest;
import org.example.procurementservice.client.VendorApiClients.WalmartVendorClient.WalmartOrderResponse;
import org.example.procurementservice.client.VendorApiClients.WalmartVendorClient.WalmartSearchRequest;
import org.example.procurementservice.client.VendorApiClients.WalmartVendorClient.WalmartSearchResponse;
import org.example.procurementservice.document.PriceSnapshot.VendorProductResult;
import org.example.procurementservice.exception.VendorUnavailableException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Single choke point for every outbound call to a vendor API.
 *
 * Responsibilities:
 * <ul>
 *   <li>Rate limit each vendor independently via per-vendor {@link Semaphore}s
 *       (held for {@code rateWindowMs} after the call).</li>
 *   <li>Wrap every call in a resilience4j {@link CircuitBreaker}
 *       ({@code amazonClient} / {@code walmartClient} / {@code krogerClient}).</li>
 *   <li>Normalize vendor-specific response DTOs into the generic
 *       {@link VendorProductResult} shape used by the optimizer.</li>
 *   <li>Return {@code Optional.empty()} from search when a vendor is
 *       unavailable (graceful degradation — excluded from the plan with a
 *       warning) and throw {@link VendorUnavailableException} from order
 *       placement (sub-order is marked FAILED).</li>
 * </ul>
 */
@Component
@Slf4j
public class VendorCallGateway {

    private static final String CB_AMAZON  = "amazonClient";
    private static final String CB_WALMART = "walmartClient";
    private static final String CB_KROGER  = "krogerClient";

    private static final long RATE_WINDOW_MS = 1_000L;

    private final AmazonVendorClient  amazonClient;
    private final WalmartVendorClient walmartClient;
    private final KrogerVendorClient  krogerClient;

    private final Semaphore amazonRateLimiter;
    private final Semaphore walmartRateLimiter;
    private final Semaphore krogerRateLimiter;
    private final ScheduledExecutorService rateLimiterScheduler;

    private final CircuitBreaker amazonBreaker;
    private final CircuitBreaker walmartBreaker;
    private final CircuitBreaker krogerBreaker;

    private final long acquireTimeoutMs;

    public VendorCallGateway(
            AmazonVendorClient  amazonClient,
            WalmartVendorClient walmartClient,
            KrogerVendorClient  krogerClient,
            @Qualifier("amazonRateLimiter")  Semaphore amazonRateLimiter,
            @Qualifier("walmartRateLimiter") Semaphore walmartRateLimiter,
            @Qualifier("krogerRateLimiter")  Semaphore krogerRateLimiter,
            ScheduledExecutorService rateLimiterScheduler,
            CircuitBreakerRegistry cbRegistry,
            @Value("${procurement.vendor-call.acquire-timeout-ms:2000}") long acquireTimeoutMs) {

        this.amazonClient  = amazonClient;
        this.walmartClient = walmartClient;
        this.krogerClient  = krogerClient;
        this.amazonRateLimiter  = amazonRateLimiter;
        this.walmartRateLimiter = walmartRateLimiter;
        this.krogerRateLimiter  = krogerRateLimiter;
        this.rateLimiterScheduler = rateLimiterScheduler;
        this.amazonBreaker  = cbRegistry.circuitBreaker(CB_AMAZON);
        this.walmartBreaker = cbRegistry.circuitBreaker(CB_WALMART);
        this.krogerBreaker  = cbRegistry.circuitBreaker(CB_KROGER);
        this.acquireTimeoutMs = acquireTimeoutMs;
    }

    // ----------------------------------------------------------------
    // Public — price search
    // ----------------------------------------------------------------

    /**
     * @return normalised product results for this vendor, or {@code Optional.empty()}
     *         if the vendor is unavailable / rate-limit-timed-out / circuit open.
     */
    public Optional<List<VendorProductResult>> searchPrices(String vendorName, String query) {
        return switch (vendorName.toLowerCase()) {
            case "amazon"  -> guardedSearch(amazonBreaker,  amazonRateLimiter,  vendorName,
                    () -> mapAmazon(amazonClient.searchItems(new AmazonSearchRequest(query, 5))));
            case "walmart" -> guardedSearch(walmartBreaker, walmartRateLimiter, vendorName,
                    () -> mapWalmart(walmartClient.searchItems(new WalmartSearchRequest(query, 5))));
            case "kroger"  -> guardedSearch(krogerBreaker,  krogerRateLimiter,  vendorName,
                    () -> mapKroger(krogerClient.searchProducts(new KrogerSearchRequest(query, 5))));
            default -> {
                log.warn("Unknown vendor '{}', skipping price search", vendorName);
                yield Optional.empty();
            }
        };
    }

    // ----------------------------------------------------------------
    // Public — order placement
    // ----------------------------------------------------------------

    /**
     * Places a sub-order with the given vendor. Returns the vendor's order reference
     * on success; throws {@link VendorUnavailableException} on any failure
     * (the caller marks the line item FAILED).
     */
    public String placeOrder(String vendorName, String vendorSku, BigDecimal quantity) {
        return switch (vendorName.toLowerCase()) {
            case "amazon"  -> guardedOrder(amazonBreaker,  amazonRateLimiter,  vendorName,
                    () -> {
                        AmazonOrderResponse r = amazonClient.placeOrder(new AmazonOrderRequest(vendorSku, quantity));
                        return r.amazonOrderId();
                    });
            case "walmart" -> guardedOrder(walmartBreaker, walmartRateLimiter, vendorName,
                    () -> {
                        WalmartOrderResponse r = walmartClient.placeOrder(new WalmartOrderRequest(vendorSku, quantity));
                        return r.purchaseOrderId();
                    });
            case "kroger"  -> guardedOrder(krogerBreaker,  krogerRateLimiter,  vendorName,
                    () -> {
                        KrogerOrderResponse r = krogerClient.placeOrder(new KrogerOrderRequest(vendorSku, quantity.intValue()));
                        return r.id();
                    });
            default -> throw new VendorUnavailableException("Unknown vendor: " + vendorName);
        };
    }

    // ----------------------------------------------------------------
    // Guarded-call scaffolding
    // ----------------------------------------------------------------

    private Optional<List<VendorProductResult>> guardedSearch(
            CircuitBreaker cb, Semaphore limiter, String vendorName,
            java.util.function.Supplier<List<VendorProductResult>> call) {

        if (!acquire(limiter, vendorName)) {
            return Optional.empty();
        }
        try {
            return Optional.of(cb.executeSupplier(call));
        } catch (CallNotPermittedException e) {
            log.warn("Circuit breaker OPEN for {} — skipping price search", vendorName);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Vendor {} price search failed: {}", vendorName, e.getMessage());
            return Optional.empty();
        } finally {
            scheduleRelease(limiter);
        }
    }

    private String guardedOrder(
            CircuitBreaker cb, Semaphore limiter, String vendorName,
            java.util.function.Supplier<String> call) {

        if (!acquire(limiter, vendorName)) {
            throw new VendorUnavailableException(
                    "Rate limit acquire timed out for " + vendorName);
        }
        try {
            return cb.executeSupplier(call);
        } catch (CallNotPermittedException e) {
            throw new VendorUnavailableException(
                    "Circuit breaker OPEN for " + vendorName, e);
        } catch (VendorUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new VendorUnavailableException(
                    "Order placement failed at " + vendorName + ": " + e.getMessage(), e);
        } finally {
            scheduleRelease(limiter);
        }
    }

    private boolean acquire(Semaphore limiter, String vendorName) {
        try {
            boolean ok = limiter.tryAcquire(acquireTimeoutMs, TimeUnit.MILLISECONDS);
            if (!ok) {
                log.warn("Rate limit acquire timed out for {} after {}ms", vendorName, acquireTimeoutMs);
            }
            return ok;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void scheduleRelease(Semaphore limiter) {
        Runnable release = limiter::release;
        rateLimiterScheduler.schedule(release, RATE_WINDOW_MS, TimeUnit.MILLISECONDS);
    }

    // ----------------------------------------------------------------
    // Vendor-specific → generic mapping
    // ----------------------------------------------------------------

    private List<VendorProductResult> mapAmazon(AmazonSearchResponse r) {
        if (r == null || r.searchResult() == null) return Collections.emptyList();
        return r.searchResult().stream()
                .map(it -> VendorProductResult.builder()
                        .vendorSku(it.asin())
                        .productName(it.title())
                        .unitPrice(it.price())
                        .unit(null)
                        .inStock(true)
                        .url(it.detailPageUrl())
                        .build())
                .toList();
    }

    private List<VendorProductResult> mapWalmart(WalmartSearchResponse r) {
        if (r == null || r.items() == null) return Collections.emptyList();
        return r.items().stream()
                .map(it -> VendorProductResult.builder()
                        .vendorSku(it.itemId())
                        .productName(it.name())
                        .unitPrice(it.salePrice())
                        .unit(null)
                        .inStock(true)
                        .url(it.productUrl())
                        .build())
                .toList();
    }

    private List<VendorProductResult> mapKroger(KrogerSearchResponse r) {
        if (r == null || r.data() == null) return Collections.emptyList();
        return r.data().stream()
                .map(p -> {
                    BigDecimal price = (p.items() == null || p.items().isEmpty())
                            ? null
                            : p.items().get(0).regular();
                    String unit = (p.items() == null || p.items().isEmpty())
                            ? null
                            : p.items().get(0).uom();
                    return VendorProductResult.builder()
                            .vendorSku(p.productId())
                            .productName(p.description())
                            .unitPrice(price)
                            .unit(unit)
                            .inStock(true)
                            .url(null)
                            .build();
                })
                .filter(v -> v.getUnitPrice() != null)
                .toList();
    }
}

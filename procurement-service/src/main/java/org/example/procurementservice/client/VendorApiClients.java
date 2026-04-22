package org.example.procurementservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;
import java.util.List;

/**
 * Feign clients for external vendor product-search and order-placement APIs.
 *
 * Each vendor gets its own @FeignClient so Resilience4j circuit breakers
 * can be configured independently (Amazon: 1 req/sec, Walmart: 5 req/sec,
 * Kroger: burst-sensitive).
 *
 * url is read from application.yml (spring.cloud.openfeign.client.config)
 * rather than Eureka — these are external HTTP endpoints, not internal services.
 *
 * The actual request/response shapes are vendor-specific; they are mapped
 * into the generic PriceSnapshot.VendorProductResult list in the service layer.
 * These interfaces model the shape the service calls; concrete FeignClient
 * decoders translate the vendor's JSON into these types.
 */
public class VendorApiClients {

    // ----------------------------------------------------------------
    // Amazon Product Advertising API v5
    // ----------------------------------------------------------------

    /**
     * Circuit breaker name: "amazon"
     * Rate limit: 1 request/second per credential set.
     * Throttle managed by AmazonRateLimiter (Semaphore with 1 permit,
     * released after 1000ms — see config/VendorRateLimiterConfig).
     */
    @FeignClient(name = "amazon-vendor-client", url = "${vendor.amazon.api-base-url}")
    public interface AmazonVendorClient {

        /**
         * Searches for products matching a keyword query.
         * Returns a raw list of matching product results which the service
         * maps into PriceSnapshot.VendorProductResult objects.
         */
        @PostMapping("/paapi5/searchitems")
        AmazonSearchResponse searchItems(@RequestBody AmazonSearchRequest request);

        /**
         * Places a sub-order for a specific product ASIN.
         * Returns a vendor order reference (Amazon order ID) on success.
         */
        @PostMapping("/paapi5/orders")
        AmazonOrderResponse placeOrder(@RequestBody AmazonOrderRequest request);

        // ---- Inner request / response types (Amazon-specific) ----

        record AmazonSearchRequest(String keywords, int itemCount) {}

        record AmazonSearchResponse(List<AmazonItem> searchResult) {
            record AmazonItem(String asin, String title,
                              BigDecimal price, String detailPageUrl) {}
        }

        record AmazonOrderRequest(String asin, BigDecimal quantity) {}

        record AmazonOrderResponse(String amazonOrderId, String status) {}
    }

    // ----------------------------------------------------------------
    // Walmart Open API
    // ----------------------------------------------------------------

    /**
     * Circuit breaker name: "walmart"
     * Rate limit: 5 requests/second.
     */
    @FeignClient(name = "walmart-vendor-client", url = "${vendor.walmart.api-base-url}")
    public interface WalmartVendorClient {

        @PostMapping("/v3/items/search")
        WalmartSearchResponse searchItems(@RequestBody WalmartSearchRequest request);

        @PostMapping("/v3/orders")
        WalmartOrderResponse placeOrder(@RequestBody WalmartOrderRequest request);

        // ---- Inner types ----

        record WalmartSearchRequest(String query, int limit) {}

        record WalmartSearchResponse(List<WalmartItem> items) {
            record WalmartItem(String itemId, String name,
                               BigDecimal salePrice, String productUrl) {}
        }

        record WalmartOrderRequest(String itemId, BigDecimal quantity) {}

        record WalmartOrderResponse(String purchaseOrderId, String orderStatus) {}
    }

    // ----------------------------------------------------------------
    // Kroger API
    // ----------------------------------------------------------------

    /**
     * Circuit breaker name: "kroger"
     * Rate limit: burst-sensitive — managed by Resilience4j RateLimiter
     * with limitForPeriod=3, limitRefreshPeriod=1s.
     */
    @FeignClient(name = "kroger-vendor-client", url = "${vendor.kroger.api-base-url}")
    public interface KrogerVendorClient {

        @PostMapping("/products")
        KrogerSearchResponse searchProducts(@RequestBody KrogerSearchRequest request);

        @PostMapping("/cart/add")
        KrogerOrderResponse placeOrder(@RequestBody KrogerOrderRequest request);

        // ---- Inner types ----

        record KrogerSearchRequest(String term, int limit) {}

        record KrogerSearchResponse(List<KrogerProduct> data) {
            record KrogerProduct(String productId, String description,
                                 List<KrogerPrice> items) {
                record KrogerPrice(BigDecimal regular, String size, String uom) {}
            }
        }

        record KrogerOrderRequest(String productId, int quantity) {}

        record KrogerOrderResponse(String id, String status) {}
    }
}

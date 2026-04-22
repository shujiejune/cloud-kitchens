package org.example.ordersservice.client;

import org.example.catalogservice.dto.CatalogItemResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Feign client for catalog-service.
 *
 * name = Eureka service name that catalog-service registers under.
 * Internal service-to-service traffic resolves via Eureka (lb://) — it
 * does NOT go through the gateway.
 *
 * The JWT from the original operator request is forwarded via the
 * Authorization header so catalog-service can enforce tenant isolation.
 * The RequestInterceptor bean that performs the forward is declared in
 * orders-service's config package.
 *
 * Used by OrdersServiceImpl when hydrating OrderResponse — order rows
 * store only catalogItemId, so the human-readable item name is looked
 * up via this batch call rather than copied into the shared table.
 */
@FeignClient(name = "catalog-service")
public interface CatalogClient {

    /**
     * Fetches catalog item metadata (name, unit, preferredQty) for a
     * set of IDs, scoped to the operator identified by the JWT.
     *
     * Maps to GET /api/v1/catalog/items/batch?ids=1,2,3
     */
    @GetMapping("/api/v1/catalog/items/batch")
    List<CatalogItemResponse> getItemsByIds(
            @RequestParam List<Long> ids,
            @RequestHeader("Authorization") String bearerToken);
}

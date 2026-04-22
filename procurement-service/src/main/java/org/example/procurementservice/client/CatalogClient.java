package org.example.procurementservice.client;

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
 * The Gateway is NOT in the call path for internal service-to-service
 * calls — Feign resolves the URL directly via Eureka (lb:// scheme).
 *
 * The JWT from the original operator request is forwarded via the
 * Authorization header so catalog-service can enforce tenant isolation.
 * This is set automatically by a RequestInterceptor bean (in config/)
 * that reads the token from the current request context.
 */
@FeignClient(name = "catalog-service")
public interface CatalogClient {

    /**
     * Fetches catalog item metadata (name, unit, preferredQty) for a
     * set of IDs, scoped to the operator identified by the JWT.
     *
     * Maps to GET /api/v1/catalog/items/batch?ids=1,2,3
     *
     * Called during plan generation so the optimization engine knows
     * the item name and preferredQty without a separate DB lookup.
     */
    @GetMapping("/api/v1/catalog/items/batch")
    List<CatalogItemResponse> getItemsByIds(
            @RequestParam List<Long> ids,
            @RequestHeader("Authorization") String bearerToken);
}

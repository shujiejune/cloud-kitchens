package org.example.procurementservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.procurementservice.dao.VendorDAO;
import org.example.procurementservice.entity.Vendor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds the three known vendors (Amazon, Walmart, Kroger) on startup if absent.
 * Idempotent — each vendor is inserted only when {@code findByName} returns empty,
 * so concurrent startups across multiple procurement-service replicas are safe
 * (the unique index on {@code name} makes a duplicate insert fail, not corrupt).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VendorSeeder implements CommandLineRunner {

    private final VendorDAO vendorDAO;

    @Value("${vendor.amazon.api-base-url}")  private String amazonBaseUrl;
    @Value("${vendor.walmart.api-base-url}") private String walmartBaseUrl;
    @Value("${vendor.kroger.api-base-url}")  private String krogerBaseUrl;

    @Override
    public void run(String... args) {
        upsert("Amazon",  amazonBaseUrl);
        upsert("Walmart", walmartBaseUrl);
        upsert("Kroger",  krogerBaseUrl);
    }

    private void upsert(String name, String apiBaseUrl) {
        vendorDAO.findByName(name).ifPresentOrElse(
                existing -> log.debug("Vendor {} already present (id={})", name, existing.getId()),
                () -> {
                    Vendor v = Vendor.builder()
                            .name(name)
                            .apiBaseUrl(apiBaseUrl)
                            .isActive(true)
                            .build();
                    vendorDAO.save(v);
                    log.info("Seeded vendor {}", name);
                });
    }
}

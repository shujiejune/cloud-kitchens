package org.example.procurementservice.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Registry of external vendor platforms the system can price-check and order from.
 *
 * MySQL table: vendors
 *
 * Seeded at deploy time with three rows:
 *   { id=1, name="Amazon",  apiBaseUrl="https://api.amazon.com/paapi5", active=true }
 *   { id=2, name="Walmart", apiBaseUrl="https://developer.api.walmart.com/api-proxy", active=true }
 *   { id=3, name="Kroger",  apiBaseUrl="https://api.kroger.com/v1", active=true }
 *
 * Feign clients in procurement-service use apiBaseUrl at runtime.
 * Circuit breaker name = vendor name (lowercase), e.g. "amazon".
 */
@Entity
@Table(name = "vendors")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Vendor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Display name shown in the UI, e.g. "Amazon". */
    @Column(nullable = false, unique = true, length = 100)
    private String name;

    /** Root URL for the vendor's product search / order API. */
    @Column(name = "api_base_url", nullable = false, length = 255)
    private String apiBaseUrl;

    /**
     * If false, this vendor is excluded from plan generation entirely.
     * Allows admins to disable a vendor without code changes (e.g. API key expired).
     */
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;
}

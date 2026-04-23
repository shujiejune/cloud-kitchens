-- Vendor catalogue (seeded at startup by VendorSeeder)
CREATE TABLE IF NOT EXISTS vendors (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    name         VARCHAR(100) NOT NULL,
    api_base_url VARCHAR(255) NOT NULL,
    is_active    BIT(1)       NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE INDEX idx_vendors_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Submitted purchase orders (write-side owner; orders-service reads these)
CREATE TABLE IF NOT EXISTS orders (
    id                BIGINT         NOT NULL AUTO_INCREMENT,
    operator_id       BIGINT         NOT NULL,
    status            VARCHAR(20)    NOT NULL,
    total_cost        DECIMAL(12, 2),
    estimated_savings DECIMAL(12, 2),
    submitted_at      DATETIME(6),
    created_at        DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_orders_operator          (operator_id),
    INDEX idx_orders_operator_submitted (operator_id, submitted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Individual vendor sub-orders within an order
CREATE TABLE IF NOT EXISTS order_line_items (
    id               BIGINT         NOT NULL AUTO_INCREMENT,
    order_id         BIGINT         NOT NULL,
    catalog_item_id  BIGINT         NOT NULL,
    vendor_id        BIGINT         NOT NULL,
    quantity         DECIMAL(10, 2) NOT NULL,
    unit_price       DECIMAL(10, 4) NOT NULL,
    line_total       DECIMAL(12, 2) NOT NULL,
    vendor_order_ref VARCHAR(255),
    sub_order_status VARCHAR(20)    NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_oli_order            (order_id),
    INDEX idx_oli_catalog_item     (catalog_item_id),
    CONSTRAINT fk_oli_order  FOREIGN KEY (order_id)  REFERENCES orders(id)  ON DELETE CASCADE,
    CONSTRAINT fk_oli_vendor FOREIGN KEY (vendor_id) REFERENCES vendors(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

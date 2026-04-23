CREATE TABLE IF NOT EXISTS catalog_items (
    id            BIGINT         NOT NULL AUTO_INCREMENT,
    operator_id   BIGINT         NOT NULL,
    name          VARCHAR(255)   NOT NULL,
    category      VARCHAR(100),
    unit          VARCHAR(50)    NOT NULL,
    preferred_qty DECIMAL(10, 2) NOT NULL,
    created_at    DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_catalog_operator          (operator_id),
    INDEX idx_catalog_operator_category (operator_id, category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

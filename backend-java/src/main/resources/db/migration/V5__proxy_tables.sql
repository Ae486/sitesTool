CREATE TABLE proxy (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    ip              VARCHAR(45) NOT NULL,
    port            INT NOT NULL,
    protocol        VARCHAR(10) NOT NULL DEFAULT 'HTTP',
    region          VARCHAR(50),
    provider        VARCHAR(100),
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    success_count   INT NOT NULL DEFAULT 0,
    fail_count      INT NOT NULL DEFAULT 0,
    avg_latency_ms  INT NOT NULL DEFAULT 0,
    last_checked_at DATETIME,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ip_port (ip, port),
    INDEX idx_active_latency (is_active, avg_latency_ms)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE proxy_health_log (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    proxy_id        BIGINT NOT NULL,
    success         BOOLEAN NOT NULL,
    latency_ms      INT,
    error_message   VARCHAR(500),
    checked_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (proxy_id) REFERENCES proxy(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

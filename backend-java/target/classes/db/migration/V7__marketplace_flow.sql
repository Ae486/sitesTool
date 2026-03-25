CREATE TABLE marketplace_flow (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    author_id       BIGINT NOT NULL,
    flow_id         BIGINT NOT NULL,
    title           VARCHAR(200) NOT NULL,
    description     TEXT,
    cover_image     VARCHAR(500),
    version         INT NOT NULL DEFAULT 1,
    visibility      VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',
    download_count  INT NOT NULL DEFAULT 0,
    avg_rating      DECIMAL(2,1) NOT NULL DEFAULT 0.0,
    dsl_snapshot    TEXT NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_author (author_id),
    INDEX idx_downloads (download_count DESC),
    INDEX idx_active_visibility (is_active, visibility)
);

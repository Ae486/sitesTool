CREATE TABLE proxy_api_configs (
    id         BIGINT       PRIMARY KEY AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    name       VARCHAR(100) NOT NULL,
    base_url   VARCHAR(500) NOT NULL,
    params_json TEXT,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_pac_user (user_id)
);

CREATE TABLE tunnel_proxy_configs (
    id         BIGINT       PRIMARY KEY AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    name       VARCHAR(100) NOT NULL,
    protocol   VARCHAR(10)  NOT NULL DEFAULT 'http',
    host       VARCHAR(255) NOT NULL,
    port       INT          NOT NULL,
    username   VARCHAR(200),
    password   VARCHAR(200),
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tpc_user (user_id)
);

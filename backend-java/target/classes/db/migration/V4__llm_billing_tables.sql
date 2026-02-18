-- V4: LLM billing tables (account + transaction)
-- Reserved slot V4 used for LLM integration (Phase 7)

CREATE TABLE account (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id    BIGINT NOT NULL UNIQUE,
    balance    BIGINT NOT NULL DEFAULT 0,
    frozen     BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_account_user (user_id)
);

CREATE TABLE transaction (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT NOT NULL,
    execution_id    VARCHAR(64),
    type            VARCHAR(20) NOT NULL,
    amount          BIGINT NOT NULL,
    balance_after   BIGINT NOT NULL,
    idempotency_key VARCHAR(128) UNIQUE,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tx_user_time (user_id, created_at DESC)
);

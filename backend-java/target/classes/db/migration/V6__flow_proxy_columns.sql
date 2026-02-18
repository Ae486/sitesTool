ALTER TABLE automation_flows
    ADD COLUMN use_proxy BOOLEAN NOT NULL DEFAULT FALSE AFTER cdp_user_data_dir,
    ADD COLUMN proxy_id BIGINT NULL AFTER use_proxy;

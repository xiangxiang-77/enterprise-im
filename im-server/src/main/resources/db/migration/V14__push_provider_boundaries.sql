CREATE TABLE IF NOT EXISTS push_device_tokens (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    platform VARCHAR(32) NOT NULL,
    provider VARCHAR(32) NOT NULL,
    token VARCHAR(512) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_push_device_tokens_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_push_device_tokens_user_enabled ON push_device_tokens(user_id, enabled);

CREATE TABLE IF NOT EXISTS push_deliveries (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    device_token_id VARCHAR(64),
    provider VARCHAR(32) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    message_id VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    reason VARCHAR(256),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_push_deliveries_user FOREIGN KEY (user_id) REFERENCES users(id)
);

INSERT INTO system_configs(config_key, config_value) VALUES
    ('push.apns.provider', 'disabled'),
    ('push.fcm.provider', 'disabled'),
    ('push.vendor.provider', 'disabled');

CREATE TABLE IF NOT EXISTS auth_verification_codes (
    id VARCHAR(64) PRIMARY KEY,
    account VARCHAR(128) NOT NULL,
    purpose VARCHAR(32) NOT NULL,
    provider VARCHAR(32) NOT NULL,
    code_hash VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    expires_at TIMESTAMP NOT NULL,
    consumed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_auth_codes_account_purpose_status ON auth_verification_codes(account, purpose, status, expires_at);

INSERT INTO system_configs(config_key, config_value) VALUES
    ('auth.password.provider', 'demo'),
    ('auth.sms.provider', 'disabled'),
    ('auth.sms.ttlSeconds', '300'),
    ('auth.sso.provider', 'disabled'),
    ('auth.biometric.provider', 'client_unavailable');

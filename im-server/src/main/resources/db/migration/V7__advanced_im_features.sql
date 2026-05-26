ALTER TABLE messages ADD COLUMN expire_after_read BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE messages ADD COLUMN destroyed_at TIMESTAMP;

CREATE TABLE IF NOT EXISTS favorite_messages (
    user_id VARCHAR(64) NOT NULL,
    message_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, message_id),
    CONSTRAINT fk_favorite_messages_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_favorite_messages_message FOREIGN KEY (message_id) REFERENCES messages(id)
);

CREATE TABLE IF NOT EXISTS screenshot_events (
    id VARCHAR(64) PRIMARY KEY,
    conversation_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_screenshot_events_conversation FOREIGN KEY (conversation_id) REFERENCES conversations(id),
    CONSTRAINT fk_screenshot_events_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS sensitive_words (
    id VARCHAR(64) PRIMARY KEY,
    word VARCHAR(128) NOT NULL UNIQUE,
    action VARCHAR(32) NOT NULL DEFAULT 'block',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS risk_events (
    id VARCHAR(64) PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    user_id VARCHAR(64),
    conversation_id VARCHAR(64),
    message_id VARCHAR(64),
    detail TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'open',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS resource_policies (
    id VARCHAR(64) PRIMARY KEY,
    policy_key VARCHAR(128) NOT NULL UNIQUE,
    policy_value VARCHAR(512) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS file_transfer_logs (
    id VARCHAR(64) PRIMARY KEY,
    file_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    direction VARCHAR(32) NOT NULL,
    progress INT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_file_transfer_logs_file FOREIGN KEY (file_id) REFERENCES files(id),
    CONSTRAINT fk_file_transfer_logs_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS workspace_apps (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    icon VARCHAR(128),
    url VARCHAR(512),
    visible_department_id VARCHAR(64),
    sort_order INT NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS app_versions (
    id VARCHAR(64) PRIMARY KEY,
    platform VARCHAR(32) NOT NULL,
    version_name VARCHAR(64) NOT NULL,
    version_code INT NOT NULL,
    download_url VARCHAR(512),
    force_update BOOLEAN NOT NULL DEFAULT FALSE,
    notes VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS system_configs (
    config_key VARCHAR(128) PRIMARY KEY,
    config_value TEXT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO resource_policies(id, policy_key, policy_value) VALUES
    ('policy_file_types', 'allowed_file_types', 'pdf,doc,docx,xls,xlsx,ppt,pptx,png,jpg,jpeg,gif,mp4,m4a,zip'),
    ('policy_max_file_size', 'max_file_size_mb', '200'),
    ('policy_original_image', 'original_image_default', 'false'),
    ('policy_resume_upload', 'resume_upload_enabled', 'true');

INSERT INTO system_configs(config_key, config_value) VALUES
    ('theme.primaryColor', '#0066FF'),
    ('startup.slogan', '安全的企业级即时通讯'),
    ('features.darkMode', 'true'),
    ('features.screenshotNotice', 'true'),
    ('features.burnAfterReading', 'true'),
    ('security.forwardPolicy', 'internal_only');

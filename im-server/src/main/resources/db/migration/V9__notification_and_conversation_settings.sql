ALTER TABLE conversation_members ADD COLUMN screenshot_notice BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE conversation_members ADD COLUMN recall_notice BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE conversation_members ADD COLUMN read_after_burn BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE conversation_members ADD COLUMN strong_reminder BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE conversation_members ADD COLUMN display_member_nicknames BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE conversation_members ADD COLUMN saved_to_contacts BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS user_notification_settings (
    user_id VARCHAR(64) PRIMARY KEY,
    new_message BOOLEAN NOT NULL DEFAULT TRUE,
    calls BOOLEAN NOT NULL DEFAULT TRUE,
    detail BOOLEAN NOT NULL DEFAULT TRUE,
    sound BOOLEAN NOT NULL DEFAULT TRUE,
    vibration BOOLEAN NOT NULL DEFAULT TRUE,
    screenshot_notice BOOLEAN NOT NULL DEFAULT TRUE,
    recall_notice BOOLEAN NOT NULL DEFAULT TRUE,
    mention_alert BOOLEAN NOT NULL DEFAULT TRUE,
    dnd_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    dnd_start VARCHAR(5) NOT NULL DEFAULT '22:00',
    dnd_end VARCHAR(5) NOT NULL DEFAULT '08:00',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_notification_settings_user FOREIGN KEY (user_id) REFERENCES users(id)
);

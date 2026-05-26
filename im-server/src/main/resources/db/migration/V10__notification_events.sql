CREATE TABLE IF NOT EXISTS notification_events (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    conversation_id VARCHAR(64) NOT NULL,
    actor_id VARCHAR(64),
    message_id VARCHAR(64),
    event_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    reason VARCHAR(128),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_events_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_notification_events_conversation FOREIGN KEY (conversation_id) REFERENCES conversations(id)
);

CREATE INDEX idx_notification_events_user_created ON notification_events(user_id, created_at);

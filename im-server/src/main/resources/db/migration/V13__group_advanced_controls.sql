ALTER TABLE chat_groups ADD COLUMN join_approval_required BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS group_invites (
    token VARCHAR(128) PRIMARY KEY,
    group_id VARCHAR(64) NOT NULL,
    inviter_id VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_group_invites_group FOREIGN KEY (group_id) REFERENCES chat_groups(id),
    CONSTRAINT fk_group_invites_inviter FOREIGN KEY (inviter_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS group_join_requests (
    id VARCHAR(64) PRIMARY KEY,
    group_id VARCHAR(64) NOT NULL,
    requester_id VARCHAR(64) NOT NULL,
    invite_token VARCHAR(128),
    message VARCHAR(512),
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    handled_at TIMESTAMP,
    CONSTRAINT fk_group_join_requests_group FOREIGN KEY (group_id) REFERENCES chat_groups(id),
    CONSTRAINT fk_group_join_requests_requester FOREIGN KEY (requester_id) REFERENCES users(id)
);

CREATE INDEX idx_group_join_requests_group_status ON group_join_requests(group_id, status);

CREATE TABLE enterprises (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    logo_url VARCHAR(512),
    code VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE departments (
    id VARCHAR(64) PRIMARY KEY,
    enterprise_id VARCHAR(64) NOT NULL,
    parent_id VARCHAR(64),
    name VARCHAR(128) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_departments_enterprise FOREIGN KEY (enterprise_id) REFERENCES enterprises(id),
    CONSTRAINT fk_departments_parent FOREIGN KEY (parent_id) REFERENCES departments(id)
);

CREATE INDEX idx_departments_enterprise_parent ON departments(enterprise_id, parent_id);

CREATE TABLE users (
    id VARCHAR(64) PRIMARY KEY,
    enterprise_id VARCHAR(64),
    phone VARCHAR(32) UNIQUE,
    email VARCHAR(128),
    display_name VARCHAR(128) NOT NULL,
    avatar_url VARCHAR(512),
    short_no VARCHAR(64) UNIQUE,
    gender VARCHAR(16),
    signature VARCHAR(512),
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_users_enterprise FOREIGN KEY (enterprise_id) REFERENCES enterprises(id)
);

CREATE INDEX idx_users_enterprise ON users(enterprise_id);
CREATE INDEX idx_users_display_name ON users(display_name);

CREATE TABLE department_members (
    department_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    position_name VARCHAR(128),
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (department_id, user_id),
    CONSTRAINT fk_department_members_department FOREIGN KEY (department_id) REFERENCES departments(id),
    CONSTRAINT fk_department_members_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE device_sessions (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    device_type VARCHAR(32) NOT NULL,
    device_name VARCHAR(128),
    token_hash VARCHAR(256) NOT NULL,
    online BOOLEAN NOT NULL DEFAULT FALSE,
    last_seen_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_device_sessions_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_device_sessions_user_online ON device_sessions(user_id, online);

CREATE TABLE friend_requests (
    id VARCHAR(64) PRIMARY KEY,
    requester_id VARCHAR(64) NOT NULL,
    receiver_id VARCHAR(64) NOT NULL,
    message VARCHAR(512),
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    handled_at TIMESTAMP,
    CONSTRAINT fk_friend_requests_requester FOREIGN KEY (requester_id) REFERENCES users(id),
    CONSTRAINT fk_friend_requests_receiver FOREIGN KEY (receiver_id) REFERENCES users(id)
);

CREATE INDEX idx_friend_requests_receiver_status ON friend_requests(receiver_id, status);

CREATE TABLE friendships (
    user_id VARCHAR(64) NOT NULL,
    friend_id VARCHAR(64) NOT NULL,
    remark VARCHAR(128),
    source VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, friend_id),
    CONSTRAINT fk_friendships_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_friendships_friend FOREIGN KEY (friend_id) REFERENCES users(id)
);

CREATE TABLE blacklists (
    user_id VARCHAR(64) NOT NULL,
    blocked_user_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, blocked_user_id),
    CONSTRAINT fk_blacklists_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_blacklists_blocked_user FOREIGN KEY (blocked_user_id) REFERENCES users(id)
);

CREATE TABLE chat_groups (
    id VARCHAR(64) PRIMARY KEY,
    enterprise_id VARCHAR(64),
    owner_id VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    avatar_url VARCHAR(512),
    group_no VARCHAR(64) UNIQUE,
    notice VARCHAR(2000),
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chat_groups_enterprise FOREIGN KEY (enterprise_id) REFERENCES enterprises(id),
    CONSTRAINT fk_chat_groups_owner FOREIGN KEY (owner_id) REFERENCES users(id)
);

CREATE INDEX idx_chat_groups_enterprise ON chat_groups(enterprise_id);

CREATE TABLE group_members (
    group_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    role VARCHAR(32) NOT NULL DEFAULT 'member',
    alias VARCHAR(128),
    muted BOOLEAN NOT NULL DEFAULT FALSE,
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (group_id, user_id),
    CONSTRAINT fk_group_members_group FOREIGN KEY (group_id) REFERENCES chat_groups(id),
    CONSTRAINT fk_group_members_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE conversations (
    id VARCHAR(64) PRIMARY KEY,
    type VARCHAR(32) NOT NULL,
    target_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_conversations_type_target ON conversations(type, target_id);

CREATE TABLE conversation_members (
    conversation_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    muted BOOLEAN NOT NULL DEFAULT FALSE,
    pinned BOOLEAN NOT NULL DEFAULT FALSE,
    unread_count INT NOT NULL DEFAULT 0,
    last_read_message_id VARCHAR(64),
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (conversation_id, user_id),
    CONSTRAINT fk_conversation_members_conversation FOREIGN KEY (conversation_id) REFERENCES conversations(id),
    CONSTRAINT fk_conversation_members_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE files (
    id VARCHAR(64) PRIMARY KEY,
    uploader_id VARCHAR(64) NOT NULL,
    object_key VARCHAR(512) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(128),
    size_bytes BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'available',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    CONSTRAINT fk_files_uploader FOREIGN KEY (uploader_id) REFERENCES users(id)
);

CREATE INDEX idx_files_uploader_created ON files(uploader_id, created_at);

CREATE TABLE messages (
    id VARCHAR(64) PRIMARY KEY,
    conversation_id VARCHAR(64) NOT NULL,
    sender_id VARCHAR(64) NOT NULL,
    type VARCHAR(32) NOT NULL,
    content TEXT,
    file_id VARCHAR(64),
    status VARCHAR(32) NOT NULL DEFAULT 'sent',
    client_seq VARCHAR(128),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    edited_at TIMESTAMP,
    recalled_at TIMESTAMP,
    CONSTRAINT fk_messages_conversation FOREIGN KEY (conversation_id) REFERENCES conversations(id),
    CONSTRAINT fk_messages_sender FOREIGN KEY (sender_id) REFERENCES users(id),
    CONSTRAINT fk_messages_file FOREIGN KEY (file_id) REFERENCES files(id)
);

CREATE INDEX idx_messages_conversation_created ON messages(conversation_id, created_at);
CREATE INDEX idx_messages_sender_created ON messages(sender_id, created_at);
CREATE UNIQUE INDEX uk_messages_sender_client_seq ON messages(sender_id, client_seq);

CREATE TABLE message_receipts (
    message_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    delivered_at TIMESTAMP,
    read_at TIMESTAMP,
    PRIMARY KEY (message_id, user_id),
    CONSTRAINT fk_message_receipts_message FOREIGN KEY (message_id) REFERENCES messages(id),
    CONSTRAINT fk_message_receipts_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE message_reactions (
    message_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    reaction VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (message_id, user_id, reaction),
    CONSTRAINT fk_message_reactions_message FOREIGN KEY (message_id) REFERENCES messages(id),
    CONSTRAINT fk_message_reactions_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE message_edits (
    id VARCHAR(64) PRIMARY KEY,
    message_id VARCHAR(64) NOT NULL,
    editor_id VARCHAR(64) NOT NULL,
    old_content TEXT,
    new_content TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_message_edits_message FOREIGN KEY (message_id) REFERENCES messages(id),
    CONSTRAINT fk_message_edits_editor FOREIGN KEY (editor_id) REFERENCES users(id)
);

CREATE TABLE message_recalls (
    id VARCHAR(64) PRIMARY KEY,
    message_id VARCHAR(64) NOT NULL,
    operator_id VARCHAR(64) NOT NULL,
    reason VARCHAR(256),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_message_recalls_message FOREIGN KEY (message_id) REFERENCES messages(id),
    CONSTRAINT fk_message_recalls_operator FOREIGN KEY (operator_id) REFERENCES users(id)
);

CREATE TABLE admin_roles (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(128) NOT NULL UNIQUE,
    description VARCHAR(512)
);

CREATE TABLE admin_users (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    role_id VARCHAR(64) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_admin_users_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_admin_users_role FOREIGN KEY (role_id) REFERENCES admin_roles(id)
);

CREATE TABLE audit_logs (
    id VARCHAR(64) PRIMARY KEY,
    operator_id VARCHAR(64),
    action VARCHAR(128) NOT NULL,
    target_type VARCHAR(64),
    target_id VARCHAR(64),
    detail TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_logs_action_created ON audit_logs(action, created_at);

CREATE TABLE call_records (
    id VARCHAR(64) PRIMARY KEY,
    conversation_id VARCHAR(64),
    caller_id VARCHAR(64) NOT NULL,
    callee_id VARCHAR(64),
    group_id VARCHAR(64),
    media_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    answered_at TIMESTAMP,
    ended_at TIMESTAMP,
    turn_session_id VARCHAR(128),
    CONSTRAINT fk_call_records_conversation FOREIGN KEY (conversation_id) REFERENCES conversations(id),
    CONSTRAINT fk_call_records_caller FOREIGN KEY (caller_id) REFERENCES users(id),
    CONSTRAINT fk_call_records_callee FOREIGN KEY (callee_id) REFERENCES users(id),
    CONSTRAINT fk_call_records_group FOREIGN KEY (group_id) REFERENCES chat_groups(id)
);

INSERT INTO admin_roles(id, name, description) VALUES
    ('role_super_admin', 'SUPER_ADMIN', 'All permissions'),
    ('role_operator', 'OPERATOR_ADMIN', 'Organization and operation management'),
    ('role_auditor', 'SECURITY_AUDITOR', 'Message audit and risk control'),
    ('role_readonly_ops', 'READONLY_OPS', 'Read-only operations');

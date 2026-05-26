CREATE TABLE IF NOT EXISTS file_upload_sessions (
    id VARCHAR(64) PRIMARY KEY,
    uploader_id VARCHAR(64) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(128),
    total_size BIGINT NOT NULL,
    total_chunks INT NOT NULL,
    object_key VARCHAR(512) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'uploading',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    CONSTRAINT fk_file_upload_sessions_user FOREIGN KEY (uploader_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS file_upload_chunks (
    session_id VARCHAR(64) NOT NULL,
    chunk_index INT NOT NULL,
    size_bytes BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (session_id, chunk_index),
    CONSTRAINT fk_file_upload_chunks_session FOREIGN KEY (session_id) REFERENCES file_upload_sessions(id)
);

INSERT INTO system_configs(config_key, config_value) VALUES
    ('file.officePreview.provider', 'disabled'),
    ('file.chunkUpload.enabled', 'true');

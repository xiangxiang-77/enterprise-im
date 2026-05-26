ALTER TABLE app_versions ADD COLUMN rollout_percent INT NOT NULL DEFAULT 100;
ALTER TABLE app_versions ADD COLUMN min_version_code INT NOT NULL DEFAULT 0;
ALTER TABLE app_versions ADD COLUMN file_size BIGINT NOT NULL DEFAULT 0;
ALTER TABLE app_versions ADD COLUMN sha256 VARCHAR(128) NOT NULL DEFAULT '';
ALTER TABLE app_versions ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'active';

CREATE TABLE IF NOT EXISTS version_downloads (
    id VARCHAR(64) PRIMARY KEY,
    version_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    platform VARCHAR(32) NOT NULL,
    downloaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_version_downloads_version FOREIGN KEY (version_id) REFERENCES app_versions(id),
    CONSTRAINT fk_version_downloads_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_version_downloads_version ON version_downloads(version_id);
CREATE INDEX IF NOT EXISTS idx_version_downloads_user ON version_downloads(user_id);

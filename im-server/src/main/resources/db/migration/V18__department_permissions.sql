CREATE TABLE IF NOT EXISTS department_permissions (
    id VARCHAR(64) PRIMARY KEY,
    department_id VARCHAR(64) NOT NULL,
    can_create_group BOOLEAN NOT NULL DEFAULT TRUE,
    can_invite_external BOOLEAN NOT NULL DEFAULT FALSE,
    can_share_files BOOLEAN NOT NULL DEFAULT TRUE,
    can_video_call BOOLEAN NOT NULL DEFAULT TRUE,
    storage_quota_mb INT NOT NULL DEFAULT 1024,
    CONSTRAINT fk_department_permissions_department FOREIGN KEY (department_id) REFERENCES departments(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_department_permissions_department ON department_permissions(department_id);

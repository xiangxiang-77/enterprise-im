ALTER TABLE file_transfer_logs ADD COLUMN size_bytes BIGINT NOT NULL DEFAULT 0;

INSERT INTO resource_policies(id, policy_key, policy_value) VALUES
    ('policy_max_upload_mb_per_minute', 'max_upload_mb_per_minute', '1024'),
    ('policy_max_download_mb_per_minute', 'max_download_mb_per_minute', '2048');

INSERT INTO system_configs(config_key, config_value) VALUES
    ('device.maxOnlineDevices', '5'),
    ('device.allowUnknownDevice', 'true'),
    ('device.forceOfflineEnabled', 'true');

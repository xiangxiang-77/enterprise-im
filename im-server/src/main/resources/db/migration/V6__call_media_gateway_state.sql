ALTER TABLE call_records ADD COLUMN pjsip_session_id VARCHAR(128);
ALTER TABLE call_records ADD COLUMN media_status VARCHAR(32) DEFAULT 'signaling_only' NOT NULL;
ALTER TABLE call_records ADD COLUMN media_error VARCHAR(512);

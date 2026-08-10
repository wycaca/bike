CREATE TABLE report_export_job (
    job_id VARCHAR(36) PRIMARY KEY,
    report_type VARCHAR(24) NOT NULL CHECK (report_type IN ('REVENUE')),
    job_status VARCHAR(16) NOT NULL CHECK (
        job_status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'EXPIRED')
    ),
    requested_by VARCHAR(36) NOT NULL REFERENCES app_user(user_id),
    city_code VARCHAR(6) NOT NULL,
    from_date DATE NOT NULL,
    to_date DATE NOT NULL,
    granularity VARCHAR(16) NOT NULL CHECK (granularity IN ('DAY', 'MONTH')),
    output_file_name VARCHAR(255) NOT NULL,
    storage_key VARCHAR(128) UNIQUE,
    file_size_bytes BIGINT,
    row_count BIGINT,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    error_message VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    CHECK (from_date <= to_date),
    CHECK (file_size_bytes IS NULL OR file_size_bytes >= 0),
    CHECK (row_count IS NULL OR row_count >= 0)
);

CREATE INDEX report_export_job_queue_idx
    ON report_export_job (created_at) WHERE job_status = 'PENDING';
CREATE INDEX report_export_job_requester_idx
    ON report_export_job (requested_by, created_at DESC);
CREATE INDEX report_export_job_expiry_idx
    ON report_export_job (expires_at) WHERE job_status = 'SUCCEEDED';

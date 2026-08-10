CREATE TABLE operations_task_rule (
    rule_id VARCHAR(36) PRIMARY KEY,
    rule_name VARCHAR(100) NOT NULL,
    city_code VARCHAR(6) NOT NULL,
    org_id VARCHAR(36) NOT NULL REFERENCES organization(org_id),
    trigger_type VARCHAR(24) NOT NULL CHECK (
        trigger_type IN ('LOW_BATTERY', 'VEHICLE_FAULT', 'VEHICLE_OFFLINE', 'GEO_VIOLATION')
    ),
    threshold_value INTEGER,
    task_type VARCHAR(24) NOT NULL CHECK (
        task_type IN ('BATTERY_SWAP', 'REBALANCE', 'REPAIR', 'INSPECTION', 'RETRIEVAL', 'CLEANING')
    ),
    priority VARCHAR(16) NOT NULL CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    title_template VARCHAR(100) NOT NULL,
    description_template VARCHAR(500),
    due_minutes INTEGER NOT NULL CHECK (due_minutes BETWEEN 5 AND 10080),
    cooldown_minutes INTEGER NOT NULL CHECK (cooldown_minutes BETWEEN 0 AND 10080),
    auto_close BOOLEAN NOT NULL DEFAULT false,
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_by VARCHAR(36) REFERENCES app_user(user_id),
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (
        (trigger_type = 'LOW_BATTERY' AND threshold_value BETWEEN 1 AND 99)
        OR (trigger_type <> 'LOW_BATTERY' AND threshold_value IS NULL)
    )
);

CREATE UNIQUE INDEX operations_task_rule_name_idx
    ON operations_task_rule (city_code, org_id, rule_name);
CREATE INDEX operations_task_rule_enabled_idx
    ON operations_task_rule (city_code, enabled, trigger_type);

CREATE TABLE operations_task_batch (
    batch_id VARCHAR(36) PRIMARY KEY,
    batch_no VARCHAR(24) NOT NULL UNIQUE,
    batch_name VARCHAR(100) NOT NULL,
    city_code VARCHAR(6) NOT NULL,
    org_id VARCHAR(36) NOT NULL REFERENCES organization(org_id),
    task_type VARCHAR(24) NOT NULL,
    requested_count INTEGER NOT NULL,
    created_count INTEGER NOT NULL DEFAULT 0,
    skipped_count INTEGER NOT NULL DEFAULT 0,
    created_by VARCHAR(36) NOT NULL REFERENCES app_user(user_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE operations_task
    ALTER COLUMN created_by DROP NOT NULL,
    ADD COLUMN source_type VARCHAR(16) NOT NULL DEFAULT 'MANUAL' CHECK (
        source_type IN ('MANUAL', 'RULE', 'BATCH')
    ),
    ADD COLUMN rule_id VARCHAR(36) REFERENCES operations_task_rule(rule_id),
    ADD COLUMN batch_id VARCHAR(36) REFERENCES operations_task_batch(batch_id),
    ADD COLUMN trigger_key VARCHAR(160),
    ADD COLUMN duplicate_count INTEGER NOT NULL DEFAULT 0 CHECK (duplicate_count >= 0),
    ADD COLUMN submitted_at TIMESTAMPTZ,
    ADD COLUMN exception_type VARCHAR(32),
    ADD COLUMN exception_note VARCHAR(500),
    ADD COLUMN exception_from_status VARCHAR(20),
    ADD COLUMN exception_at TIMESTAMPTZ;

DROP INDEX operations_task_active_vehicle_idx;
ALTER TABLE operations_task DROP CONSTRAINT operations_task_task_status_check;
ALTER TABLE operations_task ADD CONSTRAINT operations_task_task_status_check CHECK (
    task_status IN ('OPEN', 'CLAIMED', 'IN_PROGRESS', 'PENDING_REVIEW', 'EXCEPTION', 'COMPLETED', 'CANCELLED')
);
CREATE UNIQUE INDEX operations_task_active_vehicle_idx
    ON operations_task (vehicle_id)
    WHERE task_status IN ('OPEN', 'CLAIMED', 'IN_PROGRESS', 'PENDING_REVIEW', 'EXCEPTION');
CREATE INDEX operations_task_rule_history_idx
    ON operations_task (vehicle_id, rule_id, created_at DESC)
    WHERE rule_id IS NOT NULL;
CREATE INDEX operations_task_batch_idx ON operations_task (batch_id) WHERE batch_id IS NOT NULL;

ALTER TABLE operations_task_event ALTER COLUMN actor_id DROP NOT NULL;
ALTER TABLE operations_task_event DROP CONSTRAINT operations_task_event_event_type_check;
ALTER TABLE operations_task_event ADD CONSTRAINT operations_task_event_event_type_check CHECK (
    event_type IN (
        'CREATED', 'CLAIMED', 'ASSIGNED', 'RELEASED', 'STARTED', 'SUBMITTED',
        'COMPLETED', 'CANCELLED', 'DEDUPLICATED', 'RULE_RECOVERED',
        'EXCEPTION_REPORTED', 'EXCEPTION_RESOLVED', 'REVIEW_APPROVED', 'REVIEW_REJECTED'
    )
);

CREATE TABLE operations_task_trigger (
    trigger_id BIGSERIAL PRIMARY KEY,
    task_id VARCHAR(36) NOT NULL REFERENCES operations_task(task_id) ON DELETE CASCADE,
    rule_id VARCHAR(36) NOT NULL REFERENCES operations_task_rule(rule_id),
    trigger_key VARCHAR(160) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    occurrence_count INTEGER NOT NULL DEFAULT 1 CHECK (occurrence_count > 0),
    first_triggered_at TIMESTAMPTZ NOT NULL,
    last_triggered_at TIMESTAMPTZ NOT NULL,
    recovered_at TIMESTAMPTZ,
    last_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    UNIQUE (task_id, rule_id, trigger_key)
);

CREATE INDEX operations_task_trigger_rule_idx
    ON operations_task_trigger (rule_id, trigger_key, active);

CREATE TABLE operations_task_evidence (
    evidence_id BIGSERIAL PRIMARY KEY,
    task_id VARCHAR(36) NOT NULL REFERENCES operations_task(task_id) ON DELETE CASCADE,
    submission_no INTEGER NOT NULL,
    result_note VARCHAR(500) NOT NULL,
    arrival_longitude NUMERIC(9, 6) NOT NULL,
    arrival_latitude NUMERIC(8, 6) NOT NULL,
    checklist JSONB NOT NULL DEFAULT '[]'::jsonb,
    removed_battery_id VARCHAR(64),
    installed_battery_id VARCHAR(64),
    parts_used JSONB NOT NULL DEFAULT '[]'::jsonb,
    target_longitude NUMERIC(9, 6),
    target_latitude NUMERIC(8, 6),
    review_status VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK (
        review_status IN ('PENDING', 'APPROVED', 'REJECTED')
    ),
    submitted_by VARCHAR(36) NOT NULL REFERENCES app_user(user_id),
    submitted_by_name VARCHAR(64) NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    reviewed_by VARCHAR(36) REFERENCES app_user(user_id),
    reviewed_by_name VARCHAR(64),
    review_note VARCHAR(500),
    reviewed_at TIMESTAMPTZ,
    UNIQUE (task_id, submission_no),
    CHECK ((target_longitude IS NULL) = (target_latitude IS NULL))
);

CREATE TABLE operations_task_attachment (
    attachment_id BIGSERIAL PRIMARY KEY,
    task_id VARCHAR(36) NOT NULL REFERENCES operations_task(task_id) ON DELETE CASCADE,
    purpose VARCHAR(16) NOT NULL CHECK (purpose IN ('BEFORE', 'AFTER', 'EXCEPTION')),
    original_name VARCHAR(255) NOT NULL,
    stored_name VARCHAR(100) NOT NULL UNIQUE,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0),
    sha256 CHAR(64) NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    uploaded_by VARCHAR(36) NOT NULL REFERENCES app_user(user_id),
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE operations_task_evidence_attachment (
    evidence_id BIGINT NOT NULL REFERENCES operations_task_evidence(evidence_id) ON DELETE CASCADE,
    attachment_id BIGINT NOT NULL REFERENCES operations_task_attachment(attachment_id) ON DELETE CASCADE,
    purpose VARCHAR(16) NOT NULL CHECK (purpose IN ('BEFORE', 'AFTER')),
    PRIMARY KEY (evidence_id, attachment_id)
);

CREATE TABLE operations_task_exception (
    exception_id BIGSERIAL PRIMARY KEY,
    task_id VARCHAR(36) NOT NULL REFERENCES operations_task(task_id) ON DELETE CASCADE,
    exception_type VARCHAR(32) NOT NULL CHECK (
        exception_type IN ('VEHICLE_NOT_FOUND', 'ACCESS_BLOCKED', 'SAFETY_RISK', 'PARTS_SHORTAGE', 'OTHER')
    ),
    note VARCHAR(500) NOT NULL,
    reported_by VARCHAR(36) NOT NULL REFERENCES app_user(user_id),
    reported_by_name VARCHAR(64) NOT NULL,
    reported_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolution_action VARCHAR(16) CHECK (resolution_action IN ('REOPEN', 'CLOSE')),
    resolution_note VARCHAR(500),
    resolved_by VARCHAR(36) REFERENCES app_user(user_id),
    resolved_by_name VARCHAR(64),
    resolved_at TIMESTAMPTZ
);

CREATE TABLE operations_task_exception_attachment (
    exception_id BIGINT NOT NULL REFERENCES operations_task_exception(exception_id) ON DELETE CASCADE,
    attachment_id BIGINT NOT NULL REFERENCES operations_task_attachment(attachment_id) ON DELETE CASCADE,
    PRIMARY KEY (exception_id, attachment_id)
);

CREATE INDEX operations_task_evidence_task_idx
    ON operations_task_evidence (task_id, submission_no DESC);
CREATE INDEX operations_task_attachment_task_idx
    ON operations_task_attachment (task_id, uploaded_at DESC);
CREATE INDEX operations_task_exception_task_idx
    ON operations_task_exception (task_id, reported_at DESC);

CREATE TABLE operations_task (
    task_id VARCHAR(36) PRIMARY KEY,
    task_no VARCHAR(24) NOT NULL UNIQUE,
    task_type VARCHAR(24) NOT NULL CHECK (
        task_type IN ('BATTERY_SWAP', 'REBALANCE', 'REPAIR', 'INSPECTION', 'RETRIEVAL', 'CLEANING')
    ),
    task_status VARCHAR(20) NOT NULL CHECK (
        task_status IN ('OPEN', 'CLAIMED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')
    ),
    priority VARCHAR(16) NOT NULL CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    title VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    vehicle_id VARCHAR(32) NOT NULL REFERENCES vehicle(vehicle_id),
    city_code VARCHAR(6) NOT NULL,
    area_code VARCHAR(6) NOT NULL,
    org_id VARCHAR(36) NOT NULL REFERENCES organization(org_id),
    target_name VARCHAR(100),
    source_longitude NUMERIC(9, 6),
    source_latitude NUMERIC(8, 6),
    battery_percent SMALLINT CHECK (battery_percent BETWEEN 0 AND 100),
    assignee_id VARCHAR(36) REFERENCES app_user(user_id),
    created_by VARCHAR(36) NOT NULL REFERENCES app_user(user_id),
    due_at TIMESTAMPTZ,
    claimed_at TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    result_note VARCHAR(500),
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK ((source_longitude IS NULL) = (source_latitude IS NULL))
);

CREATE UNIQUE INDEX operations_task_active_vehicle_idx
    ON operations_task (vehicle_id)
    WHERE task_status IN ('OPEN', 'CLAIMED', 'IN_PROGRESS');
CREATE INDEX operations_task_queue_idx
    ON operations_task (city_code, priority, due_at, created_at)
    WHERE task_status = 'OPEN';
CREATE INDEX operations_task_assignee_idx
    ON operations_task (assignee_id, task_status, updated_at DESC);
CREATE INDEX operations_task_filter_idx
    ON operations_task (city_code, task_status, task_type, updated_at DESC);

CREATE TABLE operations_task_event (
    event_id BIGSERIAL PRIMARY KEY,
    task_id VARCHAR(36) NOT NULL REFERENCES operations_task(task_id) ON DELETE CASCADE,
    event_type VARCHAR(20) NOT NULL CHECK (
        event_type IN ('CREATED', 'CLAIMED', 'ASSIGNED', 'RELEASED', 'STARTED', 'COMPLETED', 'CANCELLED')
    ),
    from_status VARCHAR(20),
    to_status VARCHAR(20) NOT NULL,
    actor_id VARCHAR(36) NOT NULL REFERENCES app_user(user_id),
    actor_name VARCHAR(64) NOT NULL,
    note VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX operations_task_event_task_idx
    ON operations_task_event (task_id, created_at, event_id);

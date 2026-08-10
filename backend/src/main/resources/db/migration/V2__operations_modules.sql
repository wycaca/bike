CREATE TABLE organization (
    org_id VARCHAR(36) PRIMARY KEY,
    parent_org_id VARCHAR(36) REFERENCES organization(org_id),
    org_name VARCHAR(64) NOT NULL,
    org_type VARCHAR(20) NOT NULL CHECK (org_type IN ('COMPANY', 'REGION', 'TEAM')),
    city_code VARCHAR(6),
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'DISABLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (parent_org_id, org_name)
);

CREATE INDEX organization_parent_idx ON organization (parent_org_id, status);

CREATE TABLE app_user (
    user_id VARCHAR(36) PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(64) NOT NULL,
    phone VARCHAR(20),
    org_id VARCHAR(36) NOT NULL REFERENCES organization(org_id),
    role VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN', 'OPERATOR', 'AUDITOR')),
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'DISABLED')),
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX app_user_org_role_idx ON app_user (org_id, role, status);

CREATE TABLE audit_log (
    audit_id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(36),
    username VARCHAR(64) NOT NULL,
    org_id VARCHAR(36),
    action VARCHAR(32) NOT NULL,
    resource_type VARCHAR(32) NOT NULL,
    resource_id VARCHAR(64),
    request_method VARCHAR(10) NOT NULL,
    request_path VARCHAR(255) NOT NULL,
    client_ip VARCHAR(64),
    status_code INTEGER NOT NULL,
    duration_ms BIGINT NOT NULL,
    detail JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX audit_log_time_idx ON audit_log (created_at DESC);
CREATE INDEX audit_log_user_time_idx ON audit_log (user_id, created_at DESC);

CREATE TABLE geofence (
    fence_id VARCHAR(36) PRIMARY KEY,
    org_id VARCHAR(36) NOT NULL REFERENCES organization(org_id),
    fence_name VARCHAR(64) NOT NULL,
    city_code VARCHAR(6) NOT NULL,
    fence_type VARCHAR(20) NOT NULL CHECK (fence_type IN ('OPERATION', 'NO_RIDE', 'NO_PARK')),
    boundary geometry(Polygon, 4326) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'DISABLED')),
    created_by VARCHAR(36),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX geofence_city_status_idx ON geofence (city_code, status);
CREATE INDEX geofence_boundary_idx ON geofence USING GIST (boundary);

CREATE TABLE parking_point (
    point_id VARCHAR(36) PRIMARY KEY,
    org_id VARCHAR(36) NOT NULL REFERENCES organization(org_id),
    point_name VARCHAR(64) NOT NULL,
    city_code VARCHAR(6) NOT NULL,
    location geometry(Point, 4326) NOT NULL,
    radius_meters NUMERIC(7, 2) NOT NULL CHECK (radius_meters BETWEEN 10 AND 2000),
    capacity INTEGER NOT NULL CHECK (capacity BETWEEN 1 AND 10000),
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'DISABLED')),
    created_by VARCHAR(36),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX parking_point_city_status_idx ON parking_point (city_code, status);
CREATE INDEX parking_point_location_idx ON parking_point USING GIST (location);

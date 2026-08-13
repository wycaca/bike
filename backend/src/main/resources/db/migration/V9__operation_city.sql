CREATE TABLE operation_city (
    city_code VARCHAR(6) PRIMARY KEY CHECK (city_code ~ '^[0-9]{6}$'),
    city_name VARCHAR(64) NOT NULL,
    org_id VARCHAR(36) NOT NULL REFERENCES organization(org_id),
    center_longitude NUMERIC(9, 6) NOT NULL CHECK (center_longitude BETWEEN -180 AND 180),
    center_latitude NUMERIC(8, 6) NOT NULL CHECK (center_latitude BETWEEN -90 AND 90),
    min_longitude NUMERIC(9, 6) NOT NULL CHECK (min_longitude BETWEEN -180 AND 180),
    min_latitude NUMERIC(8, 6) NOT NULL CHECK (min_latitude BETWEEN -90 AND 90),
    max_longitude NUMERIC(9, 6) NOT NULL CHECK (max_longitude BETWEEN -180 AND 180),
    max_latitude NUMERIC(8, 6) NOT NULL CHECK (max_latitude BETWEEN -90 AND 90),
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'DISABLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (org_id),
    CHECK (min_longitude < max_longitude AND min_latitude < max_latitude),
    CHECK (center_longitude BETWEEN min_longitude AND max_longitude),
    CHECK (center_latitude BETWEEN min_latitude AND max_latitude)
);

CREATE INDEX operation_city_status_name_idx ON operation_city (status, city_name);

CREATE EXTENSION IF NOT EXISTS timescaledb;
CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE vehicle (
    vehicle_id VARCHAR(32) PRIMARY KEY,
    company_id VARCHAR(16) NOT NULL,
    lock_id VARCHAR(32) NOT NULL UNIQUE,
    controller_id VARCHAR(32) NOT NULL UNIQUE,
    plate_number VARCHAR(32),
    filing_code VARCHAR(32),
    model VARCHAR(64) NOT NULL,
    batch_no VARCHAR(32),
    operation_city_code VARCHAR(6) NOT NULL,
    operation_area_code VARCHAR(6) NOT NULL,
    launch_date DATE NOT NULL,
    lifecycle_status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX vehicle_city_status_idx
    ON vehicle (operation_city_code, lifecycle_status);

CREATE TABLE vehicle_position (
    vehicle_id VARCHAR(32) NOT NULL,
    reported_at TIMESTAMPTZ NOT NULL,
    longitude NUMERIC(9, 6) NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    latitude NUMERIC(8, 6) NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    position geometry(Point, 4326) NOT NULL,
    accuracy_meters NUMERIC(7, 2),
    speed_kmh NUMERIC(6, 2),
    direction_degrees SMALLINT CHECK (direction_degrees BETWEEN 0 AND 359),
    satellite_count SMALLINT,
    battery_percent SMALLINT CHECK (battery_percent BETWEEN 0 AND 100),
    remaining_range_km NUMERIC(6, 2),
    lock_status VARCHAR(16) NOT NULL,
    ride_status VARCHAR(16) NOT NULL,
    controller_status VARCHAR(16) NOT NULL,
    signal_strength SMALLINT,
    fault_codes JSONB NOT NULL DEFAULT '[]'::jsonb,
    source VARCHAR(32) NOT NULL,
    raw_payload JSONB NOT NULL,
    PRIMARY KEY (vehicle_id, reported_at)
);

SELECT create_hypertable('vehicle_position', 'reported_at', if_not_exists => TRUE);

CREATE INDEX vehicle_position_vehicle_time_idx
    ON vehicle_position (vehicle_id, reported_at DESC);
CREATE INDEX vehicle_position_geo_idx
    ON vehicle_position USING GIST (position);

CREATE TABLE vehicle_latest (
    vehicle_id VARCHAR(32) PRIMARY KEY REFERENCES vehicle(vehicle_id),
    reported_at TIMESTAMPTZ NOT NULL,
    longitude NUMERIC(9, 6) NOT NULL,
    latitude NUMERIC(8, 6) NOT NULL,
    position geometry(Point, 4326) NOT NULL,
    accuracy_meters NUMERIC(7, 2),
    speed_kmh NUMERIC(6, 2),
    direction_degrees SMALLINT,
    satellite_count SMALLINT,
    battery_percent SMALLINT,
    remaining_range_km NUMERIC(6, 2),
    lock_status VARCHAR(16) NOT NULL,
    ride_status VARCHAR(16) NOT NULL,
    controller_status VARCHAR(16) NOT NULL,
    online BOOLEAN NOT NULL,
    signal_strength SMALLINT,
    fault_codes JSONB NOT NULL DEFAULT '[]'::jsonb,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX vehicle_latest_geo_idx
    ON vehicle_latest USING GIST (position);
CREATE INDEX vehicle_latest_status_idx
    ON vehicle_latest (online, controller_status, battery_percent);

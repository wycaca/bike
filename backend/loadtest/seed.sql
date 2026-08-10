\set ON_ERROR_STOP on
\if :{?vehicle_count}
\else
\set vehicle_count 5000
\endif
\if :{?points_per_vehicle}
\else
\set points_per_vehicle 100
\endif

BEGIN;

-- 使用固定前缀保证重复执行得到同一规模的数据, 且清理范围不会影响日常 Mock 样本.
DELETE FROM vehicle_position WHERE vehicle_id LIKE 'LT-%';
DELETE FROM vehicle_latest WHERE vehicle_id LIKE 'LT-%';
DELETE FROM vehicle WHERE vehicle_id LIKE 'LT-%';

CREATE TEMP TABLE load_test_vehicle_seed ON COMMIT DROP AS
WITH generated AS (
    SELECT id,
           CASE WHEN id % 2 = 1 THEN 'BJ' ELSE 'SH' END AS city_prefix,
           CASE WHEN id % 2 = 1 THEN '110000' ELSE '310000' END AS city_code,
           CASE WHEN id % 2 = 1 THEN '110105' ELSE '310115' END AS area_code,
           CASE WHEN id % 2 = 1 THEN 116.200000 ELSE 121.300000 END AS base_longitude,
           CASE WHEN id % 2 = 1 THEN 39.750000 ELSE 31.100000 END AS base_latitude
    FROM generate_series(1, :vehicle_count::integer) AS source(id)
)
SELECT id,
       'LT-' || city_prefix || '-' || lpad(id::text, 6, '0') AS vehicle_id,
       city_code,
       area_code,
       round((base_longitude + (id % 100) * 0.003)::numeric, 6) AS longitude,
       round((base_latitude + ((id - 1) / 100 % 100) * 0.003)::numeric, 6) AS latitude,
       15 + id % 85 AS battery_percent,
       id % 20 <> 0 AS online
FROM generated;

INSERT INTO vehicle (
    vehicle_id, company_id, lock_id, controller_id, plate_number, filing_code,
    model, batch_no, operation_city_code, operation_area_code, launch_date,
    lifecycle_status
)
SELECT vehicle_id,
       'YD-MOCK',
       'LT-LOCK-' || lpad(id::text, 6, '0'),
       'LT-CTRL-' || lpad(id::text, 6, '0'),
       CASE WHEN city_code = '110000' THEN '京A-LT' ELSE '沪A-LT' END || lpad(id::text, 6, '0'),
       city_code || '-LT-' || lpad(id::text, 6, '0'),
       '雅迪换电款-MOCK',
       'LT-2026-01',
       city_code,
       area_code,
       DATE '2026-01-01',
       CASE WHEN id % 50 = 0 THEN 'MAINTENANCE' ELSE 'OPERATING' END
FROM load_test_vehicle_seed;

INSERT INTO vehicle_latest (
    vehicle_id, reported_at, longitude, latitude, position, accuracy_meters,
    speed_kmh, direction_degrees, satellite_count, battery_percent,
    remaining_range_km, lock_status, ride_status, controller_status,
    online, signal_strength, fault_codes
)
SELECT vehicle_id,
       TIMESTAMPTZ '2026-08-10 00:00:00+00',
       longitude,
       latitude,
       ST_SetSRID(ST_MakePoint(longitude, latitude), 4326),
       5.0,
       0.0,
       id % 360,
       12,
       battery_percent,
       round((battery_percent * 0.55)::numeric, 2),
       'LOCKED',
       'IDLE',
       CASE WHEN id % 97 = 0 THEN 'FAULT' ELSE 'NORMAL' END,
       online,
       -45 - id % 40,
       CASE WHEN id % 97 = 0 THEN '["E101"]'::jsonb ELSE '[]'::jsonb END
FROM load_test_vehicle_seed;

-- 压测轨迹只验证查询规模和索引, 不替代用于界面验收的道路级 Mock 轨迹.
WITH points AS (
    SELECT seed.*,
           point_no,
           round((seed.longitude + (point_no % 10 - 5) * 0.000020)::numeric, 6) AS point_longitude,
           round((seed.latitude + sin(point_no / 4.0) * 0.000100)::numeric, 6) AS point_latitude
    FROM load_test_vehicle_seed seed
    CROSS JOIN generate_series(0, :points_per_vehicle::integer - 1) AS series(point_no)
)
INSERT INTO vehicle_position (
    vehicle_id, reported_at, longitude, latitude, position, accuracy_meters,
    speed_kmh, direction_degrees, satellite_count, battery_percent,
    remaining_range_km, lock_status, ride_status, controller_status,
    signal_strength, fault_codes, source, raw_payload
)
SELECT vehicle_id,
       TIMESTAMPTZ '2026-08-01 00:00:00+00' + point_no * INTERVAL '5 minutes',
       point_longitude,
       point_latitude,
       ST_SetSRID(ST_MakePoint(point_longitude, point_latitude), 4326),
       5.0,
       12.0 + point_no % 8,
       point_no * 7 % 360,
       12,
       greatest(10, battery_percent - point_no / 10),
       round((greatest(10, battery_percent - point_no / 10) * 0.55)::numeric, 2),
       CASE WHEN point_no = :points_per_vehicle::integer - 1 THEN 'LOCKED' ELSE 'UNLOCKED' END,
       CASE WHEN point_no = :points_per_vehicle::integer - 1 THEN 'IDLE' ELSE 'RIDING' END,
       CASE WHEN id % 97 = 0 THEN 'FAULT' ELSE 'NORMAL' END,
       -45 - id % 40,
       CASE WHEN id % 97 = 0 THEN '["E101"]'::jsonb ELSE '[]'::jsonb END,
       'LOAD_TEST',
       jsonb_build_object('loadTest', true, 'sequence', point_no)
FROM points;

ANALYZE vehicle;
ANALYZE vehicle_latest;
ANALYZE vehicle_position;

COMMIT;

SELECT count(*) AS load_test_vehicles FROM vehicle WHERE vehicle_id LIKE 'LT-%';
SELECT count(*) AS load_test_positions FROM vehicle_position WHERE vehicle_id LIKE 'LT-%';

\set ON_ERROR_STOP on

BEGIN;

-- 仅删除压测前缀数据, 保留北京和上海的固定道路级 Mock 样本.
DELETE FROM vehicle_position WHERE vehicle_id LIKE 'LT-%';
DELETE FROM vehicle_latest WHERE vehicle_id LIKE 'LT-%';
DELETE FROM vehicle WHERE vehicle_id LIKE 'LT-%';

COMMIT;

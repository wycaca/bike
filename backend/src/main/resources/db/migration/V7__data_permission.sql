ALTER TABLE app_user
    ADD COLUMN data_scope VARCHAR(32);

UPDATE app_user
SET data_scope = CASE role
    WHEN 'ADMIN' THEN 'ALL'
    WHEN 'AUDITOR' THEN 'ORG_AND_CHILDREN'
    ELSE 'ORG_ONLY'
END;

ALTER TABLE app_user
    ALTER COLUMN data_scope SET NOT NULL,
    ADD CONSTRAINT app_user_data_scope_check
        CHECK (data_scope IN ('ALL', 'ORG_AND_CHILDREN', 'ORG_ONLY'));

ALTER TABLE vehicle
    ADD COLUMN org_id VARCHAR(36) REFERENCES organization(org_id);

-- 现有试点数据按城市归属区域组织, 未匹配组织时中止迁移, 避免车辆成为无权限归属的孤立数据.
UPDATE vehicle v
SET org_id = (
    SELECT o.org_id
    FROM organization o
    WHERE o.city_code = v.operation_city_code
    ORDER BY CASE o.org_type WHEN 'REGION' THEN 0 ELSE 1 END, o.org_id
    LIMIT 1
);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM vehicle WHERE org_id IS NULL) THEN
        RAISE EXCEPTION '存在无法匹配运营组织的车辆, 请先补齐组织城市配置';
    END IF;
END $$;

ALTER TABLE vehicle ALTER COLUMN org_id SET NOT NULL;
CREATE INDEX vehicle_org_status_idx ON vehicle (org_id, lifecycle_status);

ALTER TABLE report_export_job
    ADD COLUMN requested_org_id VARCHAR(36) REFERENCES organization(org_id),
    ADD COLUMN requested_data_scope VARCHAR(32);

UPDATE report_export_job j
SET requested_org_id = u.org_id,
    requested_data_scope = u.data_scope
FROM app_user u
WHERE u.user_id = j.requested_by;

ALTER TABLE report_export_job
    ALTER COLUMN requested_org_id SET NOT NULL,
    ALTER COLUMN requested_data_scope SET NOT NULL,
    ADD CONSTRAINT report_export_data_scope_check
        CHECK (requested_data_scope IN ('ALL', 'ORG_AND_CHILDREN', 'ORG_ONLY'));

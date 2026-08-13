ALTER TABLE report_export_job
    DROP CONSTRAINT report_export_job_report_type_check;

ALTER TABLE report_export_job
    ADD CONSTRAINT report_export_job_report_type_check
        CHECK (report_type IN ('REVENUE', 'VEHICLE_STATUS'));

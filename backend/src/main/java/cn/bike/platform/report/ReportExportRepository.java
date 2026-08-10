package cn.bike.platform.report;

import cn.bike.platform.report.ReportExportModels.ExportJob;
import cn.bike.platform.report.ReportExportModels.ExportStatus;
import cn.bike.platform.report.ReportExportModels.ReportType;
import cn.bike.platform.report.RevenueReportModels.RevenueGranularity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ReportExportRepository {

    private final JdbcClient jdbcClient;

    public ReportExportRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /** 输入: 报表参数和申请人; 输出: 已持久化的等待任务。 */
    public ExportJob create(
            ReportType reportType,
            String requestedBy,
            String cityCode,
            LocalDate fromDate,
            LocalDate toDate,
            RevenueGranularity granularity,
            String outputFileName
    ) {
        var jobId = UUID.randomUUID().toString();
        return jdbcClient.sql("""
                        INSERT INTO report_export_job (
                            job_id, report_type, job_status, requested_by, city_code,
                            from_date, to_date, granularity, output_file_name
                        ) VALUES (
                            :jobId, :reportType, 'PENDING', :requestedBy, :cityCode,
                            :fromDate, :toDate, :granularity, :outputFileName
                        ) RETURNING *
                        """)
                .param("jobId", jobId).param("reportType", reportType.name())
                .param("requestedBy", requestedBy).param("cityCode", cityCode)
                .param("fromDate", fromDate).param("toDate", toDate)
                .param("granularity", granularity.name()).param("outputFileName", outputFileName)
                .query((rs, rowNum) -> mapJob(rs)).single();
    }

    /** 输入: 申请人; 输出: 仍在等待或执行的任务数。 */
    public long countActive(String requestedBy) {
        return jdbcClient.sql("""
                        SELECT count(*) FROM report_export_job
                        WHERE requested_by = :requestedBy AND job_status IN ('PENDING', 'RUNNING')
                        """)
                .param("requestedBy", requestedBy).query(Long.class).single();
    }

    /** 输入: 任务和申请人编号; 输出: 仅属于该申请人的任务。 */
    public Optional<ExportJob> findOwned(String jobId, String requestedBy) {
        return jdbcClient.sql("""
                        SELECT * FROM report_export_job
                        WHERE job_id = :jobId AND requested_by = :requestedBy
                        """)
                .param("jobId", jobId).param("requestedBy", requestedBy)
                .query((rs, rowNum) -> mapJob(rs)).optional();
    }

    /**
     * 输入: 无; 输出: 原子领取的最早等待任务。
     * SKIP LOCKED 允许未来启动多个 Worker，同时保证单个任务只被一个进程领取。
     */
    public Optional<ExportJob> claimNext() {
        return jdbcClient.sql("""
                        WITH next_job AS (
                            SELECT job_id FROM report_export_job
                            WHERE job_status = 'PENDING'
                            ORDER BY created_at
                            FOR UPDATE SKIP LOCKED
                            LIMIT 1
                        )
                        UPDATE report_export_job job
                        SET job_status = 'RUNNING', started_at = now(), attempt_count = attempt_count + 1,
                            error_message = NULL
                        FROM next_job
                        WHERE job.job_id = next_job.job_id
                        RETURNING job.*
                        """)
                .query((rs, rowNum) -> mapJob(rs)).optional();
    }

    /** 输入: 已生成文件元数据; 输出: 更新后的成功任务数量。 */
    public int markSucceeded(String jobId, String storageKey, long fileSize, long rowCount, Duration retention) {
        return jdbcClient.sql("""
                        UPDATE report_export_job
                        SET job_status = 'SUCCEEDED', storage_key = :storageKey,
                            file_size_bytes = :fileSize, row_count = :rowCount,
                            completed_at = now(), expires_at = now() + (:retentionSeconds * interval '1 second')
                        WHERE job_id = :jobId AND job_status = 'RUNNING'
                        """)
                .param("jobId", jobId).param("storageKey", storageKey)
                .param("fileSize", fileSize).param("rowCount", rowCount)
                .param("retentionSeconds", retention.toSeconds()).update();
    }

    /** 输入: 任务和错误摘要; 输出: 更新后的失败任务数量。 */
    public int markFailed(String jobId, String errorMessage) {
        return jdbcClient.sql("""
                        UPDATE report_export_job
                        SET job_status = 'FAILED', error_message = :errorMessage, completed_at = now()
                        WHERE job_id = :jobId AND job_status = 'RUNNING'
                        """)
                .param("jobId", jobId).param("errorMessage", errorMessage).update();
    }

    /** 输入: 运行超时阈值; 输出: 重排队或终止的任务数量。 */
    public int recoverStale(Duration timeout) {
        return jdbcClient.sql("""
                        UPDATE report_export_job
                        SET job_status = CASE WHEN attempt_count >= 3 THEN 'FAILED' ELSE 'PENDING' END,
                            started_at = NULL,
                            completed_at = CASE WHEN attempt_count >= 3 THEN now() ELSE NULL END,
                            error_message = CASE WHEN attempt_count >= 3 THEN '报表进程多次中断' ELSE NULL END
                        WHERE job_status = 'RUNNING'
                          AND started_at < now() - (:timeoutSeconds * interval '1 second')
                        """)
                .param("timeoutSeconds", timeout.toSeconds()).update();
    }

    /** 输入: 无; 输出: 已标记过期任务对应的文件存储键。 */
    public List<String> expireCompleted() {
        return jdbcClient.sql("""
                        UPDATE report_export_job
                        SET job_status = 'EXPIRED'
                        WHERE job_status = 'SUCCEEDED' AND expires_at <= now()
                        RETURNING storage_key
                        """)
                .query(String.class).list();
    }

    /** 输入: 当前查询结果行; 输出: 完整报表任务模型。 */
    private ExportJob mapJob(ResultSet rs) throws SQLException {
        return new ExportJob(rs.getString("job_id"), ReportType.valueOf(rs.getString("report_type")),
                ExportStatus.valueOf(rs.getString("job_status")), rs.getString("requested_by"),
                rs.getString("city_code"), rs.getObject("from_date", LocalDate.class),
                rs.getObject("to_date", LocalDate.class),
                RevenueGranularity.valueOf(rs.getString("granularity")), rs.getString("output_file_name"),
                rs.getString("storage_key"), nullableLong(rs, "file_size_bytes"), nullableLong(rs, "row_count"),
                rs.getInt("attempt_count"), rs.getString("error_message"),
                instant(rs, "created_at"), instant(rs, "started_at"), instant(rs, "completed_at"),
                instant(rs, "expires_at"));
    }

    /** 输入: 结果集和可空长整数字段; 输出: 保留 SQL NULL 语义的 Long。 */
    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        var value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    /** 输入: 结果集和可空时间字段; 输出: UTC Instant 或 null。 */
    private java.time.Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}

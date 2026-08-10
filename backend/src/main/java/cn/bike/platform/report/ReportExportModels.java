package cn.bike.platform.report;

import cn.bike.platform.report.RevenueReportModels.RevenueGranularity;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;

public final class ReportExportModels {

    private ReportExportModels() {
    }

    public enum ReportType {
        REVENUE
    }

    public enum ExportStatus {
        PENDING,
        RUNNING,
        SUCCEEDED,
        FAILED,
        EXPIRED
    }

    public record ExportRequest(
            ReportType reportType,
            String cityCode,
            LocalDate fromDate,
            LocalDate toDate,
            RevenueGranularity granularity
    ) {
    }

    public record ExportJob(
            String jobId,
            ReportType reportType,
            ExportStatus status,
            String requestedBy,
            String cityCode,
            LocalDate fromDate,
            LocalDate toDate,
            RevenueGranularity granularity,
            String outputFileName,
            String storageKey,
            Long fileSizeBytes,
            Long rowCount,
            int attemptCount,
            String errorMessage,
            Instant createdAt,
            Instant startedAt,
            Instant completedAt,
            Instant expiresAt
    ) {
    }

    public record ExportJobView(
            String jobId,
            ReportType reportType,
            ExportStatus status,
            String outputFileName,
            Long fileSizeBytes,
            Long rowCount,
            String errorMessage,
            Instant createdAt,
            Instant startedAt,
            Instant completedAt,
            Instant expiresAt,
            boolean downloadable
    ) {
        /** 输入: 数据库任务模型; 输出: 隐藏存储键和申请人的 API 视图。 */
        public static ExportJobView from(ExportJob job) {
            return new ExportJobView(job.jobId(), job.reportType(), job.status(), job.outputFileName(),
                    job.fileSizeBytes(), job.rowCount(), job.errorMessage(), job.createdAt(), job.startedAt(),
                    job.completedAt(), job.expiresAt(), job.status() == ExportStatus.SUCCEEDED);
        }
    }

    record DownloadFile(Path path, String fileName, long size) {
    }

    record StoredFile(String storageKey, long size, long rowCount) {
    }
}

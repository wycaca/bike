package cn.bike.platform.report.export;

import cn.bike.platform.admin.AdminModels.DataScope;
import cn.bike.platform.report.export.ReportExportModels.ExportJob;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 报表导出任务队列 Mapper, 负责创建、领取、重试、完成和过期状态迁移.
 * 任务领取与状态条件在数据库原子执行, 支持多个 Worker 安全竞争.
 */
@Mapper
public interface ReportExportMapper {

    ExportJob create(
            @Param("jobId") String jobId,
            @Param("reportType") String reportType,
            @Param("requestedBy") String requestedBy,
            @Param("requestedOrgId") String requestedOrgId,
            @Param("requestedDataScope") DataScope requestedDataScope,
            @Param("cityCode") String cityCode,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("granularity") String granularity,
            @Param("outputFileName") String outputFileName
    );

    long countActive(@Param("requestedBy") String requestedBy);

    Optional<ExportJob> findOwned(@Param("jobId") String jobId, @Param("requestedBy") String requestedBy);

    Optional<ExportJob> claimNext();

    int markSucceeded(
            @Param("jobId") String jobId,
            @Param("storageKey") String storageKey,
            @Param("fileSize") long fileSize,
            @Param("rowCount") long rowCount,
            @Param("retentionSeconds") long retentionSeconds
    );

    int markFailed(@Param("jobId") String jobId, @Param("errorMessage") String errorMessage);

    int recoverStale(@Param("timeoutSeconds") long timeoutSeconds);

    List<String> expireCompleted();
}

package cn.bike.platform.report;

import cn.bike.platform.report.ReportExportModels.ExportJob;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Mapper
public interface ReportExportMapper {

    ExportJob create(
            @Param("jobId") String jobId,
            @Param("reportType") String reportType,
            @Param("requestedBy") String requestedBy,
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

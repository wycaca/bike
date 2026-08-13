package cn.bike.platform.report.export;

import cn.bike.platform.report.export.ReportExportModels.ExportJob;
import cn.bike.platform.report.export.ReportExportModels.ReportType;
import cn.bike.platform.report.revenue.RevenueReportModels.RevenueGranularity;
import cn.bike.platform.security.PlatformPrincipal;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 报表导出任务仓储, 将队列 SQL 结果转换为任务领域模型.
 * 不在内存维护任务状态, 以数据库条件更新和返回行为准.
 */
@Repository
public class ReportExportRepository {

    private final ReportExportMapper mapper;

    public ReportExportRepository(ReportExportMapper mapper) {
        this.mapper = mapper;
    }

    /** 输入: 报表参数和申请人; 输出: 已持久化的等待任务. */
    public ExportJob create(
            ReportType reportType,
            PlatformPrincipal principal,
            String cityCode,
            LocalDate fromDate,
            LocalDate toDate,
            RevenueGranularity granularity,
            String outputFileName
    ) {
        return mapper.create(UUID.randomUUID().toString(), reportType.name(), principal.userId(),
                principal.orgId(), principal.dataScope(), cityCode,
                fromDate, toDate, granularity.name(), outputFileName);
    }

    /** 输入: 申请人; 输出: 仍在等待或执行的任务数. */
    public long countActive(String requestedBy) {
        return mapper.countActive(requestedBy);
    }

    /** 输入: 任务和申请人编号; 输出: 仅属于该申请人的任务. */
    public Optional<ExportJob> findOwned(String jobId, String requestedBy) {
        return mapper.findOwned(jobId, requestedBy);
    }

    /**
     * 输入: 无; 输出: 原子领取的最早等待任务.
     * 数据库行锁确保多个 Worker 不会领取同一任务.
     */
    public Optional<ExportJob> claimNext() {
        return mapper.claimNext();
    }

    /** 输入: 已生成文件元数据; 输出: 更新后的成功任务数量. */
    public int markSucceeded(String jobId, String storageKey, long fileSize, long rowCount, Duration retention) {
        return mapper.markSucceeded(jobId, storageKey, fileSize, rowCount, retention.toSeconds());
    }

    /** 输入: 任务和错误摘要; 输出: 更新后的失败任务数量. */
    public int markFailed(String jobId, String errorMessage) {
        return mapper.markFailed(jobId, errorMessage);
    }

    /** 输入: 运行超时阈值; 输出: 重排队或终止的任务数量. */
    public int recoverStale(Duration timeout) {
        return mapper.recoverStale(timeout.toSeconds());
    }

    /** 输入: 无; 输出: 已标记过期任务对应的文件存储键. */
    public List<String> expireCompleted() {
        return mapper.expireCompleted();
    }
}

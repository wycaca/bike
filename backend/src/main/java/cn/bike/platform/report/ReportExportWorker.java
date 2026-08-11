package cn.bike.platform.report;

import cn.bike.platform.report.ReportExportModels.ExportJob;
import cn.bike.platform.report.ReportExportModels.ReportType;
import cn.bike.platform.security.DataPermissionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Profile("report-worker")
@Component
public class ReportExportWorker {

    private static final Logger LOG = LoggerFactory.getLogger(ReportExportWorker.class);
    private static final Duration FILE_RETENTION = Duration.ofHours(24);
    private final ReportExportRepository repository;
    private final RevenueReportService revenueReportService;
    private final ReportFileStorage storage;
    private final DataPermissionService dataPermissionService;

    public ReportExportWorker(
            ReportExportRepository repository,
            RevenueReportService revenueReportService,
            ReportFileStorage storage,
            DataPermissionService dataPermissionService
    ) {
        this.repository = repository;
        this.revenueReportService = revenueReportService;
        this.storage = storage;
        this.dataPermissionService = dataPermissionService;
    }

    /** 输入: 数据库等待队列; 输出: 每次串行领取并处理一个任务。 */
    @Scheduled(initialDelayString = "${app.report.worker.initial-delay-ms:1000}",
            fixedDelayString = "${app.report.worker.poll-delay-ms:1000}")
    public void processNext() {
        repository.claimNext().ifPresent(this::process);
    }

    /** 输入: 已领取任务; 输出: 成功文件元数据或失败状态。 */
    void process(ExportJob job) {
        var storageKey = "report-" + job.jobId() + ".csv";
        try {
            if (job.reportType() != ReportType.REVENUE) {
                throw new IllegalArgumentException("不支持的报表类型: " + job.reportType());
            }
            var permission = dataPermissionService.resolve(job.requestedDataScope(), job.requestedOrgId());
            var stored = storage.write(storageKey, writer -> revenueReportService.writeCsv(writer,
                    job.cityCode(), job.fromDate(), job.toDate(), job.granularity(), permission));
            repository.markSucceeded(job.jobId(), stored.storageKey(), stored.size(),
                    stored.rowCount(), FILE_RETENTION);
            LOG.info("报表任务 {} 已完成, 文件 {} 字节", job.jobId(), stored.size());
        } catch (Exception exception) {
            try {
                storage.delete(storageKey);
            } catch (RuntimeException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            repository.markFailed(job.jobId(), errorSummary(exception));
            LOG.error("报表任务 {} 执行失败", job.jobId(), exception);
        }
    }

    /** 输入: 超过 30 分钟未结束的任务; 输出: 最多重试三次或标记失败。 */
    @Scheduled(initialDelay = 30_000, fixedDelay = 300_000)
    public void recoverStaleJobs() {
        var recovered = repository.recoverStale(Duration.ofMinutes(30));
        if (recovered > 0) LOG.warn("已恢复 {} 个中断的报表任务", recovered);
    }

    /** 输入: 已超过保留期的成功任务; 输出: 清理文件并标记过期。 */
    @Scheduled(initialDelay = 60_000, fixedDelay = 3_600_000)
    public void cleanupExpiredFiles() {
        repository.expireCompleted().forEach(storage::delete);
    }

    /** 输入: 任务执行异常; 输出: 适合数据库字段长度的错误摘要。 */
    private String errorSummary(Exception exception) {
        var message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        return message.length() > 480 ? message.substring(0, 480) : message;
    }
}

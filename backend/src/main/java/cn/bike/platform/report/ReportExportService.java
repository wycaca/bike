package cn.bike.platform.report;

import cn.bike.platform.common.ConflictException;
import cn.bike.platform.common.NotFoundException;
import cn.bike.platform.report.ReportExportModels.DownloadFile;
import cn.bike.platform.report.ReportExportModels.ExportJob;
import cn.bike.platform.report.ReportExportModels.ExportJobView;
import cn.bike.platform.report.ReportExportModels.ExportRequest;
import cn.bike.platform.report.ReportExportModels.ExportStatus;
import cn.bike.platform.report.ReportExportModels.ReportType;
import cn.bike.platform.security.PlatformPrincipal;
import org.springframework.stereotype.Service;

@Service
public class ReportExportService {

    private static final int MAX_ACTIVE_JOBS_PER_USER = 3;
    private final ReportExportRepository repository;
    private final RevenueReportService revenueReportService;
    private final ReportFileStorage storage;

    public ReportExportService(
            ReportExportRepository repository,
            RevenueReportService revenueReportService,
            ReportFileStorage storage
    ) {
        this.repository = repository;
        this.revenueReportService = revenueReportService;
        this.storage = storage;
    }

    /** 输入: 导出条件和申请人; 输出: 立即返回的持久化等待任务。 */
    public ExportJobView create(ExportRequest request, PlatformPrincipal principal) {
        if (request == null || request.reportType() == null) {
            throw new IllegalArgumentException("报表类型不能为空");
        }
        if (request.reportType() != ReportType.REVENUE) {
            throw new IllegalArgumentException("暂不支持该报表类型");
        }
        revenueReportService.validateRequest(
                request.cityCode(), request.fromDate(), request.toDate(), request.granularity());
        if (repository.countActive(principal.userId()) >= MAX_ACTIVE_JOBS_PER_USER) {
            throw new ConflictException("当前已有 3 个报表任务，请等待完成后再提交");
        }
        var outputName = "revenue-" + request.cityCode() + "-"
                + request.fromDate() + "-" + request.toDate() + ".csv";
        return ExportJobView.from(repository.create(request.reportType(), principal, request.cityCode(),
                request.fromDate(), request.toDate(), request.granularity(), outputName));
    }

    /** 输入: 任务和申请人编号; 输出: 申请人可见的最新任务状态。 */
    public ExportJobView status(String jobId, String requestedBy) {
        return ExportJobView.from(findOwned(jobId, requestedBy));
    }

    /** 输入: 任务和申请人编号; 输出: 可流式下载的文件元数据。 */
    public DownloadFile download(String jobId, String requestedBy) {
        var job = findOwned(jobId, requestedBy);
        if (job.status() != ExportStatus.SUCCEEDED || job.storageKey() == null) {
            throw new ConflictException("报表尚未生成完成");
        }
        var path = storage.resolveExisting(job.storageKey());
        return new DownloadFile(path, job.outputFileName(), job.fileSizeBytes() == null ? 0 : job.fileSizeBytes());
    }

    /** 输入: 任务编号和申请人; 输出: 已校验归属的任务，否则抛出参数或不存在异常。 */
    private ExportJob findOwned(String jobId, String requestedBy) {
        if (jobId == null || !jobId.matches("^[0-9a-fA-F-]{36}$")) {
            throw new IllegalArgumentException("任务编号格式不正确");
        }
        return repository.findOwned(jobId, requestedBy)
                .orElseThrow(() -> new NotFoundException("报表任务不存在"));
    }
}

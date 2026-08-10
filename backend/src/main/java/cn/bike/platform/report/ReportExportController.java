package cn.bike.platform.report;

import cn.bike.platform.common.ApiResponse;
import cn.bike.platform.report.ReportExportModels.ExportJobView;
import cn.bike.platform.report.ReportExportModels.ExportRequest;
import cn.bike.platform.security.PlatformPrincipal;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/reports/exports")
public class ReportExportController {

    private final ReportExportService service;

    public ReportExportController(ReportExportService service) {
        this.service = service;
    }

    /** 输入: 导出条件和当前用户; 输出: 202 接受的异步报表任务。 */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<ExportJobView> create(
            @RequestBody ExportRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ApiResponse.ok(service.create(request, principal.userId()));
    }

    /** 输入: 任务编号和当前用户; 输出: 任务执行状态。 */
    @GetMapping("/{jobId}")
    public ApiResponse<ExportJobView> status(
            @PathVariable String jobId,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ApiResponse.ok(service.status(jobId, principal.userId()));
    }

    /** 输入: 已完成任务和当前用户; 输出: 不进入堆内存的文件流响应。 */
    @GetMapping("/{jobId}/file")
    public ResponseEntity<FileSystemResource> download(
            @PathVariable String jobId,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        var file = service.download(jobId, principal.userId());
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .contentLength(file.size())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8)
                                .build().toString())
                .body(new FileSystemResource(file.path()));
    }
}

package cn.bike.platform.ops;

import cn.bike.platform.common.ApiResponse;
import cn.bike.platform.ops.OperationsModels.AttachmentPurpose;
import cn.bike.platform.ops.OperationsModels.AttachmentUploadResult;
import cn.bike.platform.security.PlatformPrincipal;
import org.springframework.core.io.FileSystemResource;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.net.URI;

@RestController
@Profile("!report-worker")
@RequestMapping("/api/v1/ops")
public class OperationsAttachmentController {

    private final OperationsAttachmentService service;

    public OperationsAttachmentController(OperationsAttachmentService service) {
        this.service = service;
    }

    /** 输入: 任务、用途和图片; 输出: 完工或异常请求可引用的附件编号。 */
    @PostMapping(value = "/tasks/{taskId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<AttachmentUploadResult>> upload(
            @PathVariable String taskId,
            @RequestParam AttachmentPurpose purpose,
            @RequestParam MultipartFile file,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        var created = service.upload(taskId, purpose, file, principal);
        return ResponseEntity.created(URI.create(created.downloadUrl()))
                .body(ApiResponse.ok(created));
    }

    /** 输入: 附件编号; 输出: 通过文件资源流发送的原始凭证，不整体读入 JVM 堆。 */
    @GetMapping("/attachments/{attachmentId}")
    public ResponseEntity<FileSystemResource> download(
            @PathVariable long attachmentId,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        var file = service.download(attachmentId, principal);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.attachment().contentType()))
                .contentLength(file.attachment().sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename(file.attachment().originalName(), StandardCharsets.UTF_8)
                                .build().toString())
                .header("X-Content-SHA256", file.attachment().sha256())
                .body(new FileSystemResource(file.path()));
    }
}

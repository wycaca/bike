package cn.bike.platform.ops;

import cn.bike.platform.admin.AdminModels.UserRole;
import cn.bike.platform.common.ConflictException;
import cn.bike.platform.common.NotFoundException;
import cn.bike.platform.ops.OperationsModels.AttachmentPurpose;
import cn.bike.platform.ops.OperationsModels.AttachmentUploadResult;
import cn.bike.platform.ops.OperationsModels.StoredAttachment;
import cn.bike.platform.ops.OperationsModels.TaskStatus;
import cn.bike.platform.security.DataPermissionService;
import cn.bike.platform.security.PlatformPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.util.Optional;

@Service
@Profile("!report-worker")
public class OperationsAttachmentService {

    private final OperationsRepository repository;
    private final OperationsEvidenceStorage storage;
    private final DataPermissionService dataPermissionService;

    public OperationsAttachmentService(
            OperationsRepository repository,
            OperationsEvidenceStorage storage,
            DataPermissionService dataPermissionService
    ) {
        this.repository = repository;
        this.storage = storage;
        this.dataPermissionService = dataPermissionService;
    }

    /** 输入: 任务、凭证用途、图片和当前运维人员; 输出: 可用于完工或异常提交的附件编号。 */
    @Transactional
    public AttachmentUploadResult upload(
            String taskId,
            AttachmentPurpose purpose,
            MultipartFile file,
            PlatformPrincipal principal
    ) {
        if (principal.role() != UserRole.OPERATOR) {
            throw new AccessDeniedException("只有运维人员可以上传作业凭证");
        }
        var task = repository.findTask(taskId)
                .orElseThrow(() -> new NotFoundException("运维任务不存在: " + taskId));
        dataPermissionService.requireOrganization(dataPermissionService.resolve(principal), task.orgId());
        if (!principal.userId().equals(task.assigneeId())) {
            throw new AccessDeniedException("只能为自己领取的任务上传作业凭证");
        }
        var allowed = purpose == AttachmentPurpose.EXCEPTION
                ? task.status() == TaskStatus.CLAIMED || task.status() == TaskStatus.IN_PROGRESS
                : task.status() == TaskStatus.IN_PROGRESS;
        if (!allowed) {
            throw new ConflictException("当前任务状态不允许上传该类凭证");
        }

        var saved = storage.save(file);
        try {
            var originalName = safeOriginalName(file.getOriginalFilename());
            var attachmentId = repository.insertAttachment(taskId, purpose, originalName,
                    saved.storedName(), saved.contentType(), saved.sizeBytes(), saved.sha256(),
                    saved.storedName(), principal.userId());
            return new AttachmentUploadResult(attachmentId, purpose, originalName, saved.contentType(),
                    saved.sizeBytes(), "/api/v1/ops/attachments/" + attachmentId, java.time.Instant.now());
        } catch (RuntimeException exception) {
            storage.deleteQuietly(saved.storedName());
            throw exception;
        }
    }

    /** 输入: 附件编号; 输出: 数据库元数据和可流式读取的文件路径。 */
    public DownloadFile download(long attachmentId, PlatformPrincipal principal) {
        StoredAttachment attachment = repository.findAttachment(attachmentId)
                .orElseThrow(() -> new NotFoundException("运维凭证不存在: " + attachmentId));
        var task = repository.findTask(attachment.taskId())
                .orElseThrow(() -> new NotFoundException("运维任务不存在: " + attachment.taskId()));
        dataPermissionService.requireOrganization(dataPermissionService.resolve(principal), task.orgId());
        var path = storage.resolve(attachment.storagePath());
        if (!Files.isRegularFile(path)) {
            throw new NotFoundException("运维凭证文件已丢失: " + attachmentId);
        }
        return new DownloadFile(attachment, path);
    }

    private String safeOriginalName(String originalName) {
        var name = Optional.ofNullable(originalName).orElse("evidence-image");
        name = PathName.basename(name).replaceAll("[\\r\\n]", "_");
        return name.length() <= 255 ? name : name.substring(name.length() - 255);
    }

    public record DownloadFile(StoredAttachment attachment, java.nio.file.Path path) {
    }

    private static final class PathName {
        private PathName() {
        }

        private static String basename(String value) {
            return value.replace('\\', '/').substring(value.replace('\\', '/').lastIndexOf('/') + 1);
        }
    }
}

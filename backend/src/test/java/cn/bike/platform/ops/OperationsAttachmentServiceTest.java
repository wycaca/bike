package cn.bike.platform.ops;

import cn.bike.platform.TestDataPermissions;
import cn.bike.platform.admin.AdminModels.DataScope;
import cn.bike.platform.admin.AdminModels.UserRole;
import cn.bike.platform.ops.OperationsModels.AttachmentPurpose;
import cn.bike.platform.ops.OperationsModels.TaskItem;
import cn.bike.platform.ops.OperationsModels.TaskPriority;
import cn.bike.platform.ops.OperationsModels.TaskSourceType;
import cn.bike.platform.ops.OperationsModels.TaskStatus;
import cn.bike.platform.ops.OperationsModels.TaskType;
import cn.bike.platform.security.PlatformPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OperationsAttachmentServiceTest {

    @Test
    void 领取人可上传凭证并保存摘要() {
        var repository = mock(OperationsRepository.class);
        var storage = mock(OperationsEvidenceStorage.class);
        var service = new OperationsAttachmentService(repository, storage, TestDataPermissions.allService());
        var file = new MockMultipartFile("file", "after.png", "image/png", new byte[]{1});
        when(repository.findTask("TASK-1")).thenReturn(Optional.of(task("USR-OP-BJ")));
        when(storage.save(file)).thenReturn(new OperationsEvidenceStorage.SavedFile(
                "stored.png", "image/png", 128, "a".repeat(64)));
        when(repository.insertAttachment("TASK-1", AttachmentPurpose.AFTER, "after.png",
                "stored.png", "image/png", 128, "a".repeat(64), "stored.png", "USR-OP-BJ"))
                .thenReturn(7L);

        var result = service.upload("TASK-1", AttachmentPurpose.AFTER, file, principal("USR-OP-BJ"));

        assertThat(result.attachmentId()).isEqualTo(7L);
        assertThat(result.downloadUrl()).endsWith("/7");
    }

    @Test
    void 非领取人不能上传凭证() {
        var repository = mock(OperationsRepository.class);
        var storage = mock(OperationsEvidenceStorage.class);
        var service = new OperationsAttachmentService(repository, storage, TestDataPermissions.allService());
        when(repository.findTask("TASK-1")).thenReturn(Optional.of(task("USR-OTHER")));

        assertThatThrownBy(() -> service.upload("TASK-1", AttachmentPurpose.AFTER,
                new MockMultipartFile("file", "after.png", "image/png", new byte[]{1}),
                principal("USR-OP-BJ")))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("自己领取");
        verify(storage, never()).save(org.mockito.ArgumentMatchers.any());
        verify(repository, never()).insertAttachment(anyString(), org.mockito.ArgumentMatchers.any(),
                anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong(),
                anyString(), anyString(), anyString());
    }

    private TaskItem task(String assigneeId) {
        var now = Instant.parse("2026-08-11T01:00:00Z");
        return new TaskItem("TASK-1", "OPS-1", TaskType.REPAIR, TaskStatus.IN_PROGRESS,
                TaskPriority.HIGH, TaskSourceType.MANUAL, "维修任务", null,
                "YD-BJ-000001", "京A00001", "110000", "110105", "ORG-BJ", "北京运营中心",
                null, new BigDecimal("116.4"), new BigDecimal("39.9"), 50,
                assigneeId, "北京运维一组", "USR-ADMIN", "系统管理员", null, null,
                null, null, null, 0, now.plusSeconds(3600), now, now, null, null,
                null, null, null, null, 2, now, now);
    }

    private PlatformPrincipal principal(String userId) {
        return new PlatformPrincipal(userId, "operator.bj", "encoded", "北京运维一组",
                "ORG-BJ", "北京运营中心", UserRole.OPERATOR, DataScope.ORG_ONLY, true);
    }
}

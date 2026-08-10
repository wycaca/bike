package cn.bike.platform.ops;

import cn.bike.platform.admin.AdminModels.UserRole;
import cn.bike.platform.common.ConflictException;
import cn.bike.platform.ops.OperationsModels.AssigneeOption;
import cn.bike.platform.ops.OperationsModels.CreateTaskRequest;
import cn.bike.platform.ops.OperationsModels.OrganizationSnapshot;
import cn.bike.platform.ops.OperationsModels.TaskEventType;
import cn.bike.platform.ops.OperationsModels.TaskItem;
import cn.bike.platform.ops.OperationsModels.TaskPriority;
import cn.bike.platform.ops.OperationsModels.TaskStatus;
import cn.bike.platform.ops.OperationsModels.TaskType;
import cn.bike.platform.ops.OperationsModels.VehicleSnapshot;
import cn.bike.platform.security.PlatformPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OperationsServiceTest {

    @Test
    void 运维人员应能原子领取待领取任务() {
        var repository = mock(OperationsRepository.class);
        var service = new OperationsService(repository);
        var open = task(TaskStatus.OPEN, null, 0, TaskType.BATTERY_SWAP);
        var claimed = task(TaskStatus.CLAIMED, "USR-OP-BJ", 1, TaskType.BATTERY_SWAP);
        when(repository.findTask("TASK-1")).thenReturn(Optional.of(open), Optional.of(claimed));
        when(repository.findEligibleAssignee("USR-OP-BJ", "110000")).thenReturn(Optional.of(assignee()));
        when(repository.claim("TASK-1", "USR-OP-BJ")).thenReturn(1);
        when(repository.findEvents("TASK-1")).thenReturn(List.of());

        var result = service.claim("TASK-1", operator());

        assertThat(result.task().status()).isEqualTo(TaskStatus.CLAIMED);
        assertThat(result.task().assigneeId()).isEqualTo("USR-OP-BJ");
        verify(repository).insertEvent("TASK-1", TaskEventType.CLAIMED,
                TaskStatus.OPEN, TaskStatus.CLAIMED, "USR-OP-BJ", "北京运维一组", "抢单成功");
    }

    @Test
    void 后到的抢单请求应收到状态冲突() {
        var repository = mock(OperationsRepository.class);
        var service = new OperationsService(repository);
        when(repository.findTask("TASK-1")).thenReturn(Optional.of(task(TaskStatus.OPEN, null, 0,
                TaskType.BATTERY_SWAP)));
        when(repository.findEligibleAssignee("USR-OP-BJ", "110000")).thenReturn(Optional.of(assignee()));
        when(repository.claim("TASK-1", "USR-OP-BJ")).thenReturn(0);

        assertThatThrownBy(() -> service.claim("TASK-1", operator()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("已被领取");
    }

    @Test
    void 开始维修任务时应同步车辆生命周期() {
        var repository = mock(OperationsRepository.class);
        var service = new OperationsService(repository);
        var claimed = task(TaskStatus.CLAIMED, "USR-OP-BJ", 3, TaskType.REPAIR);
        var started = task(TaskStatus.IN_PROGRESS, "USR-OP-BJ", 4, TaskType.REPAIR);
        when(repository.findTask("TASK-1")).thenReturn(Optional.of(claimed), Optional.of(started));
        when(repository.start("TASK-1", 3, "USR-OP-BJ")).thenReturn(1);
        when(repository.findEvents("TASK-1")).thenReturn(List.of());

        var result = service.start("TASK-1", operator());

        assertThat(result.task().status()).isEqualTo(TaskStatus.IN_PROGRESS);
        verify(repository).updateVehicleLifecycle("YD-BJ-000001", "MAINTENANCE");
    }

    @Test
    void 非领取人不能开始任务() {
        var repository = mock(OperationsRepository.class);
        var service = new OperationsService(repository);
        when(repository.findTask("TASK-1")).thenReturn(Optional.of(
                task(TaskStatus.CLAIMED, "USR-OP-OTHER", 1, TaskType.REPAIR)));

        assertThatThrownBy(() -> service.start("TASK-1", operator()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("自己领取");
        verifyNoInteractionsAfterLookup(repository);
    }

    @Test
    void 同一车辆存在活跃任务时应拒绝重复创建() {
        var repository = mock(OperationsRepository.class);
        var service = new OperationsService(repository);
        var vehicle = new VehicleSnapshot("YD-BJ-000001", "110000", "110105",
                new BigDecimal("116.400000"), new BigDecimal("39.900000"), 9);
        var request = new CreateTaskRequest(TaskType.BATTERY_SWAP, TaskPriority.URGENT,
                "低电量换电", null, vehicle.vehicleId(), "ORG-BJ", null, Instant.now(), null);
        when(repository.findVehicleSnapshot(vehicle.vehicleId())).thenReturn(Optional.of(vehicle));
        when(repository.findOrganization("ORG-BJ"))
                .thenReturn(Optional.of(new OrganizationSnapshot("ORG-BJ", "110000", true)));
        doThrow(new DuplicateKeyException("active vehicle"))
                .when(repository).insertTask(anyString(), anyString(), any(), any(), any(), anyString());

        assertThatThrownBy(() -> service.create(request, admin()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("已有未结束");
    }

    /** 输入: 仓储 Mock; 输出: 验证状态更新方法没有被调用。 */
    private void verifyNoInteractionsAfterLookup(OperationsRepository repository) {
        verify(repository).findTask("TASK-1");
        verify(repository, org.mockito.Mockito.never()).start(anyString(), org.mockito.ArgumentMatchers.anyInt(), anyString());
    }

    /** 输入: 状态、领取人、版本和类型; 输出: 完整测试任务。 */
    private TaskItem task(TaskStatus status, String assigneeId, int version, TaskType type) {
        var now = Instant.parse("2026-08-10T01:00:00Z");
        return new TaskItem(
                "TASK-1", "OPS-1", type, status, TaskPriority.HIGH, "测试任务", null,
                "YD-BJ-000001", "京共享单车000001", "110000", "110105", "ORG-BJ", "北京运营中心",
                null, new BigDecimal("116.400000"), new BigDecimal("39.900000"), 10,
                assigneeId, assigneeId == null ? null : "北京运维一组", "USR-ADMIN", "系统管理员",
                now.plusSeconds(3600), status == TaskStatus.OPEN ? null : now, null, null, null,
                version, now, now
        );
    }

    /** 输入: 无; 输出: 北京可指派运维人员。 */
    private AssigneeOption assignee() {
        return new AssigneeOption("USR-OP-BJ", "北京运维一组", "13800001101", "ORG-BJ", "北京运营中心");
    }

    /** 输入: 无; 输出: 北京运维人员登录主体。 */
    private PlatformPrincipal operator() {
        return new PlatformPrincipal("USR-OP-BJ", "operator.bj", "encoded", "北京运维一组",
                "ORG-BJ", "北京运营中心", UserRole.OPERATOR, true);
    }

    /** 输入: 无; 输出: 系统管理员登录主体。 */
    private PlatformPrincipal admin() {
        return new PlatformPrincipal("USR-ADMIN", "admin", "encoded", "系统管理员",
                "ORG-HQ", "运营总部", UserRole.ADMIN, true);
    }
}

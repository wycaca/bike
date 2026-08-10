package cn.bike.platform.ops;

import cn.bike.platform.admin.AdminModels.UserRole;
import cn.bike.platform.common.ConflictException;
import cn.bike.platform.common.NotFoundException;
import cn.bike.platform.ops.OperationsModels.AssigneeOption;
import cn.bike.platform.ops.OperationsModels.AssignmentRequest;
import cn.bike.platform.ops.OperationsModels.CancellationRequest;
import cn.bike.platform.ops.OperationsModels.CompletionRequest;
import cn.bike.platform.ops.OperationsModels.CreateTaskRequest;
import cn.bike.platform.ops.OperationsModels.TaskDetail;
import cn.bike.platform.ops.OperationsModels.TaskEventType;
import cn.bike.platform.ops.OperationsModels.TaskItem;
import cn.bike.platform.ops.OperationsModels.TaskPage;
import cn.bike.platform.ops.OperationsModels.TaskStatus;
import cn.bike.platform.ops.OperationsModels.TaskSummary;
import cn.bike.platform.ops.OperationsModels.TaskType;
import cn.bike.platform.security.PlatformPrincipal;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class OperationsService {

    private static final List<String> TASK_SCOPES = List.of("ALL", "MINE", "UNASSIGNED");

    private final OperationsRepository repository;

    public OperationsService(OperationsRepository repository) {
        this.repository = repository;
    }

    // ==================== 查询能力 ====================

    /** 输入: 分页、筛选和当前用户; 输出: 按优先级排序的运维任务分页。 */
    public TaskPage findTasks(
            int page,
            int pageSize,
            String cityCode,
            TaskStatus status,
            TaskType type,
            String scope,
            String keyword,
            PlatformPrincipal principal
    ) {
        validatePage(page, pageSize);
        validateCityCode(cityCode);
        var normalizedScope = normalizeScope(scope);
        return new TaskPage(
                repository.findTasks(page, pageSize, cityCode, status, type,
                        normalizedScope, principal.userId(), keyword),
                repository.countTasks(cityCode, status, type, normalizedScope, principal.userId(), keyword),
                page,
                pageSize
        );
    }

    /** 输入: 城市和当前用户; 输出: 待领取、执行中、超时和个人任务汇总。 */
    public TaskSummary summary(String cityCode, PlatformPrincipal principal) {
        validateCityCode(cityCode);
        return repository.summary(cityCode, principal.userId());
    }

    /** 输入: 任务编号; 输出: 任务详情和不可变操作时间线。 */
    public TaskDetail detail(String taskId) {
        return detailOf(requireTask(taskId));
    }

    /** 输入: 城市; 输出: 管理员可指派的启用运维人员。 */
    public List<AssigneeOption> assignees(String cityCode) {
        validateCityCode(cityCode);
        return repository.findAssignees(cityCode);
    }

    // ==================== 任务创建与状态流转 ====================

    /** 输入: 新任务和创建者; 输出: 等待抢单或已指派的新任务。 */
    @Transactional
    public TaskDetail create(CreateTaskRequest request, PlatformPrincipal principal) {
        var vehicle = repository.findVehicleSnapshot(request.vehicleId())
                .orElseThrow(() -> new IllegalArgumentException("车辆不存在或不可执行运维任务"));
        var organization = repository.findOrganization(request.orgId())
                .orElseThrow(() -> new IllegalArgumentException("任务组织不存在"));
        if (!organization.active()
                || (organization.cityCode() != null && !organization.cityCode().equals(vehicle.cityCode()))) {
            throw new IllegalArgumentException("任务组织未启用或不负责车辆所在城市");
        }
        if (principal.role() == UserRole.OPERATOR && !principal.orgId().equals(request.orgId())) {
            throw new AccessDeniedException("运维人员只能为所属组织创建任务");
        }

        String assigneeId = blankToNull(request.assigneeId());
        AssigneeOption assignee = null;
        if (assigneeId != null) {
            requireRole(principal, UserRole.ADMIN, "只有管理员可以创建已指派任务");
            assignee = requireEligibleAssignee(assigneeId, vehicle.cityCode());
        }

        var taskId = UUID.randomUUID().toString();
        var taskNo = "OPS-" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12).toUpperCase(Locale.ROOT);
        try {
            repository.insertTask(taskId, taskNo, request, vehicle, assigneeId, principal.userId());
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("该车辆已有未结束的运维任务");
        }

        var initialStatus = assigneeId == null ? TaskStatus.OPEN : TaskStatus.CLAIMED;
        repository.insertEvent(taskId, TaskEventType.CREATED, null, initialStatus,
                principal.userId(), principal.displayName(), "创建任务");
        if (assignee != null) {
            repository.insertEvent(taskId, TaskEventType.ASSIGNED, TaskStatus.OPEN, TaskStatus.CLAIMED,
                    principal.userId(), principal.displayName(), "指派给 " + assignee.displayName());
        }
        return detail(taskId);
    }

    /** 输入: 待领取任务和当前运维人员; 输出: 原子领取后的任务详情。 */
    @Transactional
    public TaskDetail claim(String taskId, PlatformPrincipal principal) {
        requireRole(principal, UserRole.OPERATOR, "只有运维人员可以抢单");
        var task = requireTask(taskId);
        requireEligibleAssignee(principal.userId(), task.cityCode());
        if (repository.claim(taskId, principal.userId()) == 0) {
            throw new ConflictException("任务已被领取或状态已变化");
        }
        repository.insertEvent(taskId, TaskEventType.CLAIMED, TaskStatus.OPEN, TaskStatus.CLAIMED,
                principal.userId(), principal.displayName(), "抢单成功");
        return detail(taskId);
    }

    /** 输入: 任务、目标人员和管理员; 输出: 指派或改派后的任务详情。 */
    @Transactional
    public TaskDetail assign(String taskId, AssignmentRequest request, PlatformPrincipal principal) {
        requireRole(principal, UserRole.ADMIN, "只有管理员可以指派任务");
        var task = requireTask(taskId);
        var assignee = requireEligibleAssignee(request.assigneeId(), task.cityCode());
        if (repository.assign(taskId, task.version(), assignee.userId()) == 0) {
            throw stateConflict();
        }
        repository.insertEvent(taskId, TaskEventType.ASSIGNED, task.status(), TaskStatus.CLAIMED,
                principal.userId(), principal.displayName(), "指派给 " + assignee.displayName());
        return detail(taskId);
    }

    /** 输入: 已领取任务和领取人; 输出: 释放回公共任务池后的详情。 */
    @Transactional
    public TaskDetail release(String taskId, PlatformPrincipal principal) {
        requireRole(principal, UserRole.OPERATOR, "只有运维人员可以释放任务");
        var task = requireOwnedTask(taskId, principal);
        if (repository.release(taskId, task.version(), principal.userId()) == 0) {
            throw stateConflict();
        }
        repository.insertEvent(taskId, TaskEventType.RELEASED, TaskStatus.CLAIMED, TaskStatus.OPEN,
                principal.userId(), principal.displayName(), "释放回公共任务池");
        return detail(taskId);
    }

    /** 输入: 已领取任务和领取人; 输出: 开始执行并同步车辆状态后的详情。 */
    @Transactional
    public TaskDetail start(String taskId, PlatformPrincipal principal) {
        requireRole(principal, UserRole.OPERATOR, "只有运维人员可以开始任务");
        var task = requireOwnedTask(taskId, principal);
        if (repository.start(taskId, task.version(), principal.userId()) == 0) {
            throw stateConflict();
        }

        // 调度、回收任务需要体现车辆正在移动，其余现场作业统一进入维修状态。
        var vehicleStatus = task.taskType() == TaskType.REBALANCE || task.taskType() == TaskType.RETRIEVAL
                ? "DISPATCHING" : "MAINTENANCE";
        repository.updateVehicleLifecycle(task.vehicleId(), vehicleStatus);
        repository.insertEvent(taskId, TaskEventType.STARTED, TaskStatus.CLAIMED, TaskStatus.IN_PROGRESS,
                principal.userId(), principal.displayName(), "开始执行任务");
        return detail(taskId);
    }

    /** 输入: 执行中任务、结果和领取人; 输出: 完成并恢复车辆运营后的详情。 */
    @Transactional
    public TaskDetail complete(String taskId, CompletionRequest request, PlatformPrincipal principal) {
        requireRole(principal, UserRole.OPERATOR, "只有运维人员可以完成任务");
        var task = requireOwnedTask(taskId, principal);
        if (repository.complete(taskId, task.version(), principal.userId(), request.resultNote()) == 0) {
            throw stateConflict();
        }
        repository.updateVehicleLifecycle(task.vehicleId(), "OPERATING");
        repository.insertEvent(taskId, TaskEventType.COMPLETED, TaskStatus.IN_PROGRESS, TaskStatus.COMPLETED,
                principal.userId(), principal.displayName(), request.resultNote());
        return detail(taskId);
    }

    /** 输入: 未结束任务、原因和管理员; 输出: 取消任务并恢复车辆运营后的详情。 */
    @Transactional
    public TaskDetail cancel(String taskId, CancellationRequest request, PlatformPrincipal principal) {
        requireRole(principal, UserRole.ADMIN, "只有管理员可以取消任务");
        var task = requireTask(taskId);
        if (repository.cancel(taskId, task.version(), request.reason()) == 0) {
            throw stateConflict();
        }
        if (task.status() == TaskStatus.IN_PROGRESS) {
            repository.updateVehicleLifecycle(task.vehicleId(), "OPERATING");
        }
        repository.insertEvent(taskId, TaskEventType.CANCELLED, task.status(), TaskStatus.CANCELLED,
                principal.userId(), principal.displayName(), request.reason());
        return detail(taskId);
    }

    // ==================== 权限和参数校验 ====================

    /** 输入: 任务编号和当前用户; 输出: 由当前用户领取的任务。 */
    private TaskItem requireOwnedTask(String taskId, PlatformPrincipal principal) {
        var task = requireTask(taskId);
        if (!principal.userId().equals(task.assigneeId())) {
            throw new AccessDeniedException("只能操作自己领取的任务");
        }
        return task;
    }

    /** 输入: 任务编号; 输出: 存在的任务。 */
    private TaskItem requireTask(String taskId) {
        return repository.findTask(taskId)
                .orElseThrow(() -> new NotFoundException("运维任务不存在: " + taskId));
    }

    /** 输入: 用户与城市; 输出: 可在该城市执行任务的运维人员。 */
    private AssigneeOption requireEligibleAssignee(String userId, String cityCode) {
        return repository.findEligibleAssignee(userId, cityCode)
                .orElseThrow(() -> new IllegalArgumentException("指派人员未启用或不负责任务城市"));
    }

    /** 输入: 任务; 输出: 包含时间线的详情。 */
    private TaskDetail detailOf(TaskItem task) {
        return new TaskDetail(task, repository.findEvents(task.taskId()));
    }

    /** 输入: 当前用户、目标角色和错误提示; 输出: 无, 角色不匹配时拒绝操作。 */
    private void requireRole(PlatformPrincipal principal, UserRole role, String message) {
        if (principal.role() != role) {
            throw new AccessDeniedException(message);
        }
    }

    /** 输入: 分页参数; 输出: 无, 参数非法时抛出异常。 */
    private void validatePage(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("page 必须大于 0, pageSize 必须在 1 到 100 之间");
        }
    }

    /** 输入: 城市行政区划代码; 输出: 无, 非六位数字时抛出异常。 */
    private void validateCityCode(String cityCode) {
        if (cityCode == null || !cityCode.matches("\\d{6}")) {
            throw new IllegalArgumentException("cityCode 必须是 6 位行政区划代码");
        }
    }

    /** 输入: 列表范围; 输出: 标准化后的 ALL、MINE 或 UNASSIGNED。 */
    private String normalizeScope(String scope) {
        var normalized = scope == null ? "ALL" : scope.trim().toUpperCase(Locale.ROOT);
        if (!TASK_SCOPES.contains(normalized)) {
            throw new IllegalArgumentException("scope 仅支持 ALL、MINE、UNASSIGNED");
        }
        return normalized;
    }

    /** 输入: 可空字符串; 输出: 去除首尾空白后的值或 null。 */
    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 输入: 无; 输出: 统一的并发状态冲突异常。 */
    private ConflictException stateConflict() {
        return new ConflictException("任务状态已变化，请刷新后重试");
    }
}

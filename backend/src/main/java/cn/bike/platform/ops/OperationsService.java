package cn.bike.platform.ops;

import cn.bike.platform.admin.AdminModels.UserRole;
import cn.bike.platform.common.ConflictException;
import cn.bike.platform.common.NotFoundException;
import cn.bike.platform.ops.OperationsModels.AssigneeOption;
import cn.bike.platform.ops.OperationsModels.AssignmentRequest;
import cn.bike.platform.ops.OperationsModels.AttachmentPurpose;
import cn.bike.platform.ops.OperationsModels.BatchCreateResult;
import cn.bike.platform.ops.OperationsModels.BatchCreateTaskRequest;
import cn.bike.platform.ops.OperationsModels.BatchSkippedItem;
import cn.bike.platform.ops.OperationsModels.CancellationRequest;
import cn.bike.platform.ops.OperationsModels.CompletionRequest;
import cn.bike.platform.ops.OperationsModels.CreateTaskRequest;
import cn.bike.platform.ops.OperationsModels.ExceptionRequest;
import cn.bike.platform.ops.OperationsModels.ExceptionResolutionAction;
import cn.bike.platform.ops.OperationsModels.ExceptionResolutionRequest;
import cn.bike.platform.ops.OperationsModels.ReviewAction;
import cn.bike.platform.ops.OperationsModels.ReviewRequest;
import cn.bike.platform.ops.OperationsModels.ReviewStatus;
import cn.bike.platform.ops.OperationsModels.StoredAttachment;
import cn.bike.platform.ops.OperationsModels.TaskDetail;
import cn.bike.platform.ops.OperationsModels.TaskEventType;
import cn.bike.platform.ops.OperationsModels.TaskItem;
import cn.bike.platform.ops.OperationsModels.TaskPage;
import cn.bike.platform.ops.OperationsModels.TaskSourceType;
import cn.bike.platform.ops.OperationsModels.TaskStatus;
import cn.bike.platform.ops.OperationsModels.TaskSummary;
import cn.bike.platform.ops.OperationsModels.TaskType;
import cn.bike.platform.ops.OperationsModels.VehicleSnapshot;
import cn.bike.platform.security.DataPermissionService;
import cn.bike.platform.security.PlatformPrincipal;
import cn.bike.platform.vehicle.CoordinateConverter;
import cn.bike.platform.vehicle.VehicleModels.CoordinateSystem;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class OperationsService {

    private static final List<String> TASK_SCOPES = List.of("ALL", "MINE", "UNASSIGNED");

    private final OperationsRepository repository;
    private final DataPermissionService dataPermissionService;

    public OperationsService(OperationsRepository repository, DataPermissionService dataPermissionService) {
        this.repository = repository;
        this.dataPermissionService = dataPermissionService;
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
        var permission = dataPermissionService.resolve(principal);
        return new TaskPage(
                repository.findTasks(page, pageSize, cityCode, status, type,
                        normalizedScope, principal.userId(), keyword, permission),
                repository.countTasks(cityCode, status, type, normalizedScope, principal.userId(), keyword,
                        permission),
                page,
                pageSize
        );
    }

    /** 输入: 城市和当前用户; 输出: 队列、验收、异常和个人任务汇总。 */
    public TaskSummary summary(String cityCode, PlatformPrincipal principal) {
        validateCityCode(cityCode);
        return repository.summary(cityCode, principal.userId(), dataPermissionService.resolve(principal));
    }

    /** 输入: 任务编号; 输出: 任务、凭证、异常、触发和不可变时间线。 */
    public TaskDetail detail(String taskId, PlatformPrincipal principal) {
        return repository.findTaskDetail(requireTask(taskId, principal));
    }

    /** 输入: 城市; 输出: 管理员可指派的启用运维人员。 */
    public List<AssigneeOption> assignees(String cityCode, PlatformPrincipal principal) {
        validateCityCode(cityCode);
        return repository.findAssignees(cityCode, dataPermissionService.resolve(principal));
    }

    // ==================== 单条与批量创建 ====================

    /** 输入: 新任务和创建者; 输出: 等待抢单或已指派的新任务。 */
    @Transactional
    public TaskDetail create(CreateTaskRequest request, PlatformPrincipal principal) {
        var task = createInternal(request, principal, TaskSourceType.MANUAL, null);
        if (task == null) {
            throw new ConflictException("该车辆已有未结束的运维任务");
        }
        return detail(task.taskId(), principal);
    }

    /**
     * 输入: 批量任务模板、车辆列表和创建者; 输出: 创建成功与跳过原因。
     *
     * 每辆车独立插入，活跃任务冲突使用ON CONFLICT转为跳过项，不会让整批任务回滚。
     */
    @Transactional
    public BatchCreateResult createBatch(BatchCreateTaskRequest request, PlatformPrincipal principal) {
        var uniqueVehicleIds = new LinkedHashSet<String>();
        var skipped = new ArrayList<BatchSkippedItem>();
        for (var vehicleId : request.vehicleIds()) {
            var normalized = vehicleId.trim();
            if (!uniqueVehicleIds.add(normalized)) {
                skipped.add(new BatchSkippedItem(normalized, "批次内车辆编号重复"));
            }
        }
        var permission = dataPermissionService.resolve(principal);
        dataPermissionService.requireOrganization(permission, request.orgId());
        var organization = validateOrganization(request.orgId(), null);
        if (principal.role() == UserRole.OPERATOR && !principal.orgId().equals(request.orgId())) {
            throw new AccessDeniedException("运维人员只能为所属组织创建批量任务");
        }
        if (request.assigneeId() != null && !request.assigneeId().isBlank()) {
            requireRole(principal, UserRole.ADMIN, "只有管理员可以创建已指派的批量任务");
        }

        var vehicles = uniqueVehicleIds.stream()
                .map(id -> repository.findVehicleSnapshot(id).orElse(null)).toList();
        var firstVehicle = vehicles.stream().filter(java.util.Objects::nonNull)
                .filter(vehicle -> permission.includes(vehicle.orgId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("批量任务中没有可用车辆"));
        var cityCode = organization.cityCode() == null ? firstVehicle.cityCode() : organization.cityCode();
        validateOrganization(request.orgId(), cityCode);

        var batchId = UUID.randomUUID().toString();
        var batchNo = "BAT-" + randomCode();
        repository.insertBatch(batchId, batchNo, request.batchName(), cityCode, request.orgId(),
                request.taskType(), request.vehicleIds().size(), principal.userId());

        var created = new ArrayList<TaskItem>();
        for (var vehicleId : uniqueVehicleIds) {
            var vehicle = repository.findVehicleSnapshot(vehicleId).orElse(null);
            if (vehicle == null || !permission.includes(vehicle.orgId())) {
                skipped.add(new BatchSkippedItem(vehicleId, "车辆不存在、不可用或无权限"));
                continue;
            }
            if (!cityCode.equals(vehicle.cityCode())) {
                skipped.add(new BatchSkippedItem(vehicleId, "车辆不属于批次城市"));
                continue;
            }
            var itemRequest = new CreateTaskRequest(request.taskType(), request.priority(), request.title(),
                    request.description(), vehicleId, request.orgId(), request.targetName(), request.dueAt(),
                    request.assigneeId());
            var task = createInternal(itemRequest, principal, TaskSourceType.BATCH, batchId);
            if (task == null) {
                skipped.add(new BatchSkippedItem(vehicleId, "车辆已有未结束任务"));
            } else {
                created.add(task);
            }
        }
        repository.updateBatchCounts(batchId, created.size(), skipped.size());
        return new BatchCreateResult(batchId, batchNo, request.vehicleIds().size(), created, skipped);
    }

    /** 输入: 创建请求、来源和可选批次; 输出: 新任务, 冲突时返回null。 */
    private TaskItem createInternal(
            CreateTaskRequest request,
            PlatformPrincipal principal,
            TaskSourceType sourceType,
            String batchId
    ) {
        var vehicle = repository.findVehicleSnapshot(request.vehicleId()).orElse(null);
        var permission = dataPermissionService.resolve(principal);
        if (vehicle == null || !permission.includes(vehicle.orgId())) {
            if (sourceType == TaskSourceType.BATCH) {
                return null;
            }
            throw new IllegalArgumentException("车辆不存在或不可执行运维任务");
        }
        dataPermissionService.requireOrganization(permission, request.orgId());
        validateOrganization(request.orgId(), vehicle.cityCode());
        if (principal.role() == UserRole.OPERATOR && !principal.orgId().equals(request.orgId())) {
            throw new AccessDeniedException("运维人员只能为所属组织创建任务");
        }

        String assigneeId = blankToNull(request.assigneeId());
        AssigneeOption assignee = null;
        if (assigneeId != null) {
            requireRole(principal, UserRole.ADMIN, "只有管理员可以创建已指派任务");
            assignee = requireEligibleAssignee(assigneeId, vehicle.cityCode(), principal);
        }
        var taskId = UUID.randomUUID().toString();
        if (repository.insertTask(taskId, "OPS-" + randomCode(), request, vehicle, assigneeId,
                principal.userId(), sourceType, null, batchId, null) == 0) {
            return null;
        }

        var initialStatus = assigneeId == null ? TaskStatus.OPEN : TaskStatus.CLAIMED;
        var createNote = sourceType == TaskSourceType.BATCH ? "通过批量任务创建" : "创建任务";
        repository.insertEvent(taskId, TaskEventType.CREATED, null, initialStatus,
                principal.userId(), principal.displayName(), createNote);
        if (assignee != null) {
            repository.insertEvent(taskId, TaskEventType.ASSIGNED, TaskStatus.OPEN, TaskStatus.CLAIMED,
                    principal.userId(), principal.displayName(), "指派给 " + assignee.displayName());
        }
        return requireTask(taskId, principal);
    }

    // ==================== 领取、执行与验收 ====================

    @Transactional
    public TaskDetail claim(String taskId, PlatformPrincipal principal) {
        requireRole(principal, UserRole.OPERATOR, "只有运维人员可以抢单");
        var task = requireTask(taskId, principal);
        requireEligibleAssignee(principal.userId(), task.cityCode(), principal);
        if (repository.claim(taskId, principal.userId()) == 0) {
            throw new ConflictException("任务已被领取或状态已变化");
        }
        repository.insertEvent(taskId, TaskEventType.CLAIMED, TaskStatus.OPEN, TaskStatus.CLAIMED,
                principal.userId(), principal.displayName(), "抢单成功");
        return detail(taskId, principal);
    }

    @Transactional
    public TaskDetail assign(String taskId, AssignmentRequest request, PlatformPrincipal principal) {
        requireRole(principal, UserRole.ADMIN, "只有管理员可以指派任务");
        var task = requireTask(taskId, principal);
        var assignee = requireEligibleAssignee(request.assigneeId(), task.cityCode(), principal);
        if (repository.assign(taskId, task.version(), assignee.userId()) == 0) {
            throw stateConflict();
        }
        repository.insertEvent(taskId, TaskEventType.ASSIGNED, task.status(), TaskStatus.CLAIMED,
                principal.userId(), principal.displayName(), "指派给 " + assignee.displayName());
        return detail(taskId, principal);
    }

    @Transactional
    public TaskDetail release(String taskId, PlatformPrincipal principal) {
        requireRole(principal, UserRole.OPERATOR, "只有运维人员可以释放任务");
        var task = requireOwnedTask(taskId, principal);
        if (repository.release(taskId, task.version(), principal.userId()) == 0) {
            throw stateConflict();
        }
        repository.insertEvent(taskId, TaskEventType.RELEASED, TaskStatus.CLAIMED, TaskStatus.OPEN,
                principal.userId(), principal.displayName(), "释放回公共任务池");
        return detail(taskId, principal);
    }

    @Transactional
    public TaskDetail start(String taskId, PlatformPrincipal principal) {
        requireRole(principal, UserRole.OPERATOR, "只有运维人员可以开始任务");
        var task = requireOwnedTask(taskId, principal);
        if (repository.start(taskId, task.version(), principal.userId()) == 0) {
            throw stateConflict();
        }
        var vehicleStatus = task.taskType() == TaskType.REBALANCE || task.taskType() == TaskType.RETRIEVAL
                ? "DISPATCHING" : "MAINTENANCE";
        repository.updateVehicleLifecycle(task.vehicleId(), vehicleStatus);
        repository.insertEvent(taskId, TaskEventType.STARTED, TaskStatus.CLAIMED, TaskStatus.IN_PROGRESS,
                principal.userId(), principal.displayName(), "开始执行任务");
        return detail(taskId, principal);
    }

    /** 输入: 执行结果和结构化凭证; 输出: 等待管理员验收的任务详情。 */
    @Transactional
    public TaskDetail complete(String taskId, CompletionRequest request, PlatformPrincipal principal) {
        requireRole(principal, UserRole.OPERATOR, "只有运维人员可以提交完工凭证");
        var task = requireOwnedTask(taskId, principal);
        validateCompletionEvidence(task, request, principal);
        // 现场设备和地图可能使用不同坐标系, 入库前统一为 WGS84, 避免后续距离校验和地图回放混用坐标.
        var arrival = CoordinateConverter.convert(request.arrivalLongitude(), request.arrivalLatitude(),
                request.coordinateSystem(), CoordinateSystem.WGS84);
        var target = request.targetLongitude() == null ? null : CoordinateConverter.convert(
                request.targetLongitude(), request.targetLatitude(), request.coordinateSystem(), CoordinateSystem.WGS84);
        var evidenceId = repository.insertEvidence(taskId, request.resultNote(), arrival.longitude(),
                arrival.latitude(), request.checklist(), request.removedBatteryId(),
                request.installedBatteryId(), request.partsUsed(), target == null ? null : target.longitude(),
                target == null ? null : target.latitude(), principal.userId(), principal.displayName());
        repository.linkEvidenceAttachments(evidenceId, request.beforeAttachmentIds(), AttachmentPurpose.BEFORE);
        repository.linkEvidenceAttachments(evidenceId, request.afterAttachmentIds(), AttachmentPurpose.AFTER);
        if (repository.submitForReview(taskId, task.version(), principal.userId(), request.resultNote()) == 0) {
            throw stateConflict();
        }
        repository.insertEvent(taskId, TaskEventType.SUBMITTED, TaskStatus.IN_PROGRESS,
                TaskStatus.PENDING_REVIEW, principal.userId(), principal.displayName(), "提交完工凭证，等待验收");
        return detail(taskId, principal);
    }

    /** 输入: 管理员验收动作; 输出: 完成或退回执行的任务。 */
    @Transactional
    public TaskDetail review(String taskId, ReviewRequest request, PlatformPrincipal principal) {
        requireRole(principal, UserRole.ADMIN, "只有管理员可以验收任务");
        var task = requireTask(taskId, principal);
        if (task.status() != TaskStatus.PENDING_REVIEW) {
            throw new ConflictException("只有待验收任务可以执行验收");
        }
        if (request.action() == ReviewAction.REJECT && blankToNull(request.note()) == null) {
            throw new IllegalArgumentException("驳回时必须填写原因");
        }
        if (request.action() == ReviewAction.APPROVE) {
            if (repository.approve(taskId, task.version()) == 0) {
                throw stateConflict();
            }
            repository.reviewLatestEvidence(taskId, ReviewStatus.APPROVED, principal.userId(),
                    principal.displayName(), request.note());
            repository.updateVehicleLifecycle(task.vehicleId(), "OPERATING");
            repository.insertEvent(taskId, TaskEventType.REVIEW_APPROVED, TaskStatus.PENDING_REVIEW,
                    TaskStatus.COMPLETED, principal.userId(), principal.displayName(),
                    blankToNull(request.note()) == null ? "验收通过" : request.note());
        } else {
            if (repository.reject(taskId, task.version()) == 0) {
                throw stateConflict();
            }
            repository.reviewLatestEvidence(taskId, ReviewStatus.REJECTED, principal.userId(),
                    principal.displayName(), request.note());
            repository.insertEvent(taskId, TaskEventType.REVIEW_REJECTED, TaskStatus.PENDING_REVIEW,
                    TaskStatus.IN_PROGRESS, principal.userId(), principal.displayName(), request.note());
        }
        return detail(taskId, principal);
    }

    // ==================== 异常闭环与取消 ====================

    /** 输入: 异常类型、说明和现场附件; 输出: 进入待管理员处理状态的任务。 */
    @Transactional
    public TaskDetail reportException(String taskId, ExceptionRequest request, PlatformPrincipal principal) {
        requireRole(principal, UserRole.OPERATOR, "只有运维人员可以上报现场异常");
        var task = requireOwnedTask(taskId, principal);
        validateAttachments(taskId, request.attachmentIds(), AttachmentPurpose.EXCEPTION, principal, false);
        if (repository.reportException(taskId, task.version(), principal.userId(),
                request.exceptionType(), request.note()) == 0) {
            throw stateConflict();
        }
        var exceptionId = repository.insertException(taskId, request.exceptionType(), request.note(),
                principal.userId(), principal.displayName());
        repository.linkExceptionAttachments(exceptionId, request.attachmentIds());
        repository.insertEvent(taskId, TaskEventType.EXCEPTION_REPORTED, task.status(), TaskStatus.EXCEPTION,
                principal.userId(), principal.displayName(), request.note());
        return detail(taskId, principal);
    }

    /** 输入: 管理员重开或关闭动作; 输出: 异常处理后的任务。 */
    @Transactional
    public TaskDetail resolveException(
            String taskId,
            ExceptionResolutionRequest request,
            PlatformPrincipal principal
    ) {
        requireRole(principal, UserRole.ADMIN, "只有管理员可以处理任务异常");
        var task = requireTask(taskId, principal);
        if (task.status() != TaskStatus.EXCEPTION) {
            throw new ConflictException("只有异常任务可以执行异常处理");
        }
        if (repository.resolveException(taskId, task.version(), request.action(), request.note()) == 0) {
            throw stateConflict();
        }
        repository.resolveLatestException(taskId, request.action(), request.note(),
                principal.userId(), principal.displayName());
        var targetStatus = request.action() == ExceptionResolutionAction.CLOSE
                ? TaskStatus.CANCELLED
                : task.startedAt() == null ? TaskStatus.CLAIMED : TaskStatus.IN_PROGRESS;
        if (request.action() == ExceptionResolutionAction.CLOSE || targetStatus == TaskStatus.CLAIMED) {
            repository.updateVehicleLifecycle(task.vehicleId(), "OPERATING");
        }
        repository.insertEvent(taskId, TaskEventType.EXCEPTION_RESOLVED, TaskStatus.EXCEPTION, targetStatus,
                principal.userId(), principal.displayName(), request.note());
        return detail(taskId, principal);
    }

    @Transactional
    public TaskDetail cancel(String taskId, CancellationRequest request, PlatformPrincipal principal) {
        requireRole(principal, UserRole.ADMIN, "只有管理员可以取消任务");
        var task = requireTask(taskId, principal);
        if (repository.cancel(taskId, task.version(), request.reason()) == 0) {
            throw stateConflict();
        }
        if (task.startedAt() != null || task.status() == TaskStatus.PENDING_REVIEW
                || task.status() == TaskStatus.EXCEPTION) {
            repository.updateVehicleLifecycle(task.vehicleId(), "OPERATING");
        }
        repository.insertEvent(taskId, TaskEventType.CANCELLED, task.status(), TaskStatus.CANCELLED,
                principal.userId(), principal.displayName(), request.reason());
        return detail(taskId, principal);
    }

    // ==================== 凭证校验 ====================

    private void validateCompletionEvidence(
            TaskItem task,
            CompletionRequest request,
            PlatformPrincipal principal
    ) {
        if (task.status() != TaskStatus.IN_PROGRESS) {
            throw new ConflictException("只有执行中的任务可以提交完工凭证");
        }
        if ((request.targetLongitude() == null) != (request.targetLatitude() == null)) {
            throw new IllegalArgumentException("目标经纬度必须同时填写");
        }
        if (task.taskType() == TaskType.BATTERY_SWAP
                && (blankToNull(request.removedBatteryId()) == null
                || blankToNull(request.installedBatteryId()) == null)) {
            throw new IllegalArgumentException("换电任务必须记录换出和换入电池编号");
        }
        if (task.taskType() == TaskType.BATTERY_SWAP
                && request.removedBatteryId().trim().equals(request.installedBatteryId().trim())) {
            throw new IllegalArgumentException("换出和换入电池编号不能相同");
        }
        if (task.taskType() == TaskType.REBALANCE && request.targetLongitude() == null) {
            throw new IllegalArgumentException("调度任务必须记录实际到达位置");
        }
        validateAttachments(task.taskId(), request.beforeAttachmentIds(), AttachmentPurpose.BEFORE,
                principal, false);
        validateAttachments(task.taskId(), request.afterAttachmentIds(), AttachmentPurpose.AFTER,
                principal, true);
    }

    /** 输入: 任务、附件编号、用途和操作者; 输出: 无, 保证附件真实属于当前任务。 */
    private void validateAttachments(
            String taskId,
            List<Long> attachmentIds,
            AttachmentPurpose purpose,
            PlatformPrincipal principal,
            boolean required
    ) {
        var ids = attachmentIds == null ? List.<Long>of() : attachmentIds.stream().distinct().toList();
        if (required && ids.isEmpty()) {
            throw new IllegalArgumentException("至少需要上传一张处理后照片");
        }
        var attachments = repository.findAttachments(ids);
        if (attachments.size() != ids.size() || attachments.stream().anyMatch(attachment ->
                !taskId.equals(attachment.taskId()) || attachment.purpose() != purpose
                        || !principal.userId().equals(attachment.uploadedBy()))) {
            throw new IllegalArgumentException("附件不存在、用途不匹配或不属于当前操作者");
        }
    }

    // ==================== 权限和通用校验 ====================

    private TaskItem requireOwnedTask(String taskId, PlatformPrincipal principal) {
        var task = requireTask(taskId, principal);
        if (!principal.userId().equals(task.assigneeId())) {
            throw new AccessDeniedException("只能操作自己领取的任务");
        }
        return task;
    }

    private TaskItem requireTask(String taskId) {
        return repository.findTask(taskId)
                .orElseThrow(() -> new NotFoundException("运维任务不存在: " + taskId));
    }

    private TaskItem requireTask(String taskId, PlatformPrincipal principal) {
        var task = requireTask(taskId);
        dataPermissionService.requireOrganization(dataPermissionService.resolve(principal), task.orgId());
        return task;
    }

    private AssigneeOption requireEligibleAssignee(
            String userId,
            String cityCode,
            PlatformPrincipal principal
    ) {
        var assignee = repository.findEligibleAssignee(userId, cityCode)
                .orElseThrow(() -> new IllegalArgumentException("指派人员未启用或不负责任务城市"));
        dataPermissionService.requireOrganization(dataPermissionService.resolve(principal), assignee.orgId());
        return assignee;
    }

    private OperationsModels.OrganizationSnapshot validateOrganization(String orgId, String cityCode) {
        var organization = repository.findOrganization(orgId)
                .orElseThrow(() -> new IllegalArgumentException("任务组织不存在"));
        if (!organization.active() || (cityCode != null && organization.cityCode() != null
                && !organization.cityCode().equals(cityCode))) {
            throw new IllegalArgumentException("任务组织未启用或不负责车辆所在城市");
        }
        return organization;
    }

    private void requireRole(PlatformPrincipal principal, UserRole role, String message) {
        if (principal.role() != role) {
            throw new AccessDeniedException(message);
        }
    }

    private void validatePage(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("page 必须大于 0, pageSize 必须在 1 到 100 之间");
        }
    }

    private void validateCityCode(String cityCode) {
        if (cityCode == null || !cityCode.matches("\\d{6}")) {
            throw new IllegalArgumentException("cityCode 必须是 6 位行政区划代码");
        }
    }

    private String normalizeScope(String scope) {
        var normalized = scope == null ? "ALL" : scope.trim().toUpperCase(Locale.ROOT);
        if (!TASK_SCOPES.contains(normalized)) {
            throw new IllegalArgumentException("scope 仅支持 ALL、MINE、UNASSIGNED");
        }
        return normalized;
    }

    private String randomCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ConflictException stateConflict() {
        return new ConflictException("任务状态已变化，请刷新后重试");
    }
}

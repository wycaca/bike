package cn.bike.platform.ops;

import cn.bike.platform.ops.OperationsMapper.AttachmentInsert;
import cn.bike.platform.ops.OperationsMapper.AutomationRow;
import cn.bike.platform.ops.OperationsMapper.EvidenceAttachmentRow;
import cn.bike.platform.ops.OperationsMapper.EvidenceInsert;
import cn.bike.platform.ops.OperationsMapper.EvidenceRow;
import cn.bike.platform.ops.OperationsMapper.ExceptionInsert;
import cn.bike.platform.ops.OperationsMapper.ExceptionRow;
import cn.bike.platform.ops.OperationsMapper.TaskInsert;
import cn.bike.platform.ops.OperationsModels.AssigneeOption;
import cn.bike.platform.ops.OperationsModels.AttachmentPurpose;
import cn.bike.platform.ops.OperationsModels.AutomationVehicleState;
import cn.bike.platform.ops.OperationsModels.CreateTaskRequest;
import cn.bike.platform.ops.OperationsModels.EvidenceAttachment;
import cn.bike.platform.ops.OperationsModels.ExceptionResolutionAction;
import cn.bike.platform.ops.OperationsModels.ExceptionType;
import cn.bike.platform.ops.OperationsModels.OrganizationSnapshot;
import cn.bike.platform.ops.OperationsModels.ReviewStatus;
import cn.bike.platform.ops.OperationsModels.StoredAttachment;
import cn.bike.platform.ops.OperationsModels.TaskDetail;
import cn.bike.platform.ops.OperationsModels.TaskEvent;
import cn.bike.platform.ops.OperationsModels.TaskEventType;
import cn.bike.platform.ops.OperationsModels.TaskEvidence;
import cn.bike.platform.ops.OperationsModels.TaskException;
import cn.bike.platform.ops.OperationsModels.TaskItem;
import cn.bike.platform.ops.OperationsModels.TaskSourceType;
import cn.bike.platform.ops.OperationsModels.TaskStatus;
import cn.bike.platform.ops.OperationsModels.TaskSummary;
import cn.bike.platform.ops.OperationsModels.TaskTrigger;
import cn.bike.platform.ops.OperationsModels.TaskType;
import cn.bike.platform.ops.OperationsModels.VehicleSnapshot;
import cn.bike.platform.security.DataPermission;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 运维任务仓储, 负责数据库行与任务详情、证据和异常领域模型的组装.
 * 状态机不在内存中重放, Mapper 的影响行数直接表示并发更新是否成功.
 */
@Repository
public class OperationsRepository {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final OperationsMapper mapper;
    private final JsonMapper jsonMapper;

    public OperationsRepository(OperationsMapper mapper, JsonMapper jsonMapper) {
        this.mapper = mapper;
        this.jsonMapper = jsonMapper;
    }

    // ==================== 任务查询 ====================

    /** 输入: 任务筛选与分页; 输出: 按紧急程度、截止时间排序的任务列表。 */
    public List<TaskItem> findTasks(
            int page,
            int pageSize,
            String cityCode,
            TaskStatus status,
            TaskType type,
            String scope,
            String currentUserId,
            String keyword,
            DataPermission permission
    ) {
        return mapper.findTasks(cityCode, enumName(status), enumName(type), scope, currentUserId,
                fuzzyOrNull(keyword), permission, pageSize, (page - 1) * pageSize);
    }

    /** 输入: 任务筛选; 输出: 匹配任务总数。 */
    public long countTasks(
            String cityCode,
            TaskStatus status,
            TaskType type,
            String scope,
            String currentUserId,
            String keyword,
            DataPermission permission
    ) {
        return mapper.countTasks(cityCode, enumName(status), enumName(type), scope, currentUserId,
                fuzzyOrNull(keyword), permission);
    }

    /** 输入: 城市和当前用户; 输出: 队列、验收、异常、超时和个人任务汇总。 */
    public TaskSummary summary(String cityCode, String currentUserId, DataPermission permission) {
        return mapper.summary(cityCode, currentUserId, permission);
    }

    /** 输入: 任务编号; 输出: 包含人员、规则和批次显示名的任务。 */
    public Optional<TaskItem> findTask(String taskId) {
        return Optional.ofNullable(mapper.findTask(taskId));
    }

    /** 输入: 多个任务编号; 输出: 对应任务列表。 */
    public List<TaskItem> findTasksByIds(List<String> taskIds) {
        return taskIds.isEmpty() ? List.of() : mapper.findTasksByIds(taskIds);
    }

    /** 输入: 车辆编号; 输出: 当前未结束任务。 */
    public Optional<TaskItem> findActiveTaskForVehicle(String vehicleId) {
        return Optional.ofNullable(mapper.findActiveTaskForVehicle(vehicleId));
    }

    /** 输入: 任务编号; 输出: 任务、时间线、凭证、异常和自动触发记录。 */
    public TaskDetail findTaskDetail(TaskItem task) {
        return new TaskDetail(task, findEvents(task.taskId()), findEvidence(task.taskId()),
                findExceptions(task.taskId()), mapper.findTriggers(task.taskId()));
    }

    public List<TaskEvent> findEvents(String taskId) {
        return mapper.findEvents(taskId);
    }

    /** 输入: 车辆编号; 输出: 创建任务所需的车辆和最新位置快照。 */
    public Optional<VehicleSnapshot> findVehicleSnapshot(String vehicleId) {
        return Optional.ofNullable(mapper.findVehicleSnapshot(vehicleId));
    }

    /** 输入: 组织编号; 输出: 城市和启用状态快照。 */
    public Optional<OrganizationSnapshot> findOrganization(String orgId) {
        return Optional.ofNullable(mapper.findOrganization(orgId));
    }

    public Optional<AssigneeOption> findEligibleAssignee(String userId, String cityCode) {
        return Optional.ofNullable(mapper.findEligibleAssignee(userId, cityCode));
    }

    public List<AssigneeOption> findAssignees(String cityCode, DataPermission permission) {
        return mapper.findAssignees(cityCode, permission);
    }

    // ==================== 任务与批次写入 ====================

    /** 输入: 任务及来源信息; 输出: 插入行数, 活跃车辆冲突时返回 0。 */
    public int insertTask(
            String taskId,
            String taskNo,
            CreateTaskRequest request,
            VehicleSnapshot vehicle,
            String assigneeId,
            String createdBy,
            TaskSourceType sourceType,
            String ruleId,
            String batchId,
            String triggerKey
    ) {
        var status = assigneeId == null ? TaskStatus.OPEN : TaskStatus.CLAIMED;
        return mapper.insertTask(new TaskInsert(
                taskId, taskNo, request.taskType().name(), status.name(), request.priority().name(),
                sourceType.name(), request.title().trim(), blankToNull(request.description()), vehicle.vehicleId(),
                vehicle.cityCode(), vehicle.areaCode(), request.orgId(), blankToNull(request.targetName()),
                vehicle.longitude(), vehicle.latitude(), vehicle.batteryPercent(), assigneeId, createdBy,
                ruleId, batchId, triggerKey, request.dueAt()));
    }

    public void insertBatch(
            String batchId,
            String batchNo,
            String batchName,
            String cityCode,
            String orgId,
            TaskType taskType,
            int requestedCount,
            String createdBy
    ) {
        mapper.insertBatch(batchId, batchNo, batchName.trim(), cityCode, orgId, taskType.name(),
                requestedCount, createdBy);
    }

    public void updateBatchCounts(String batchId, int createdCount, int skippedCount) {
        mapper.updateBatchCounts(batchId, createdCount, skippedCount);
    }

    // ==================== 状态流转 ====================

    public int claim(String taskId, String assigneeId) {
        return mapper.claim(taskId, assigneeId);
    }

    public int assign(String taskId, int version, String assigneeId) {
        return mapper.assign(taskId, version, assigneeId);
    }

    public int release(String taskId, int version, String assigneeId) {
        return mapper.release(taskId, version, assigneeId);
    }

    public int start(String taskId, int version, String assigneeId) {
        return mapper.start(taskId, version, assigneeId);
    }

    public int submitForReview(String taskId, int version, String assigneeId, String resultNote) {
        return mapper.submitForReview(taskId, version, assigneeId, resultNote.trim());
    }

    public int approve(String taskId, int version) {
        return mapper.approve(taskId, version);
    }

    public int reject(String taskId, int version) {
        return mapper.reject(taskId, version);
    }

    public int cancel(String taskId, int version, String reason) {
        return mapper.cancel(taskId, version, reason.trim());
    }

    public int reportException(
            String taskId,
            int version,
            String assigneeId,
            ExceptionType type,
            String note
    ) {
        return mapper.reportException(taskId, version, assigneeId, type.name(), note.trim());
    }

    public int resolveException(String taskId, int version, ExceptionResolutionAction action, String note) {
        var targetStatus = action == ExceptionResolutionAction.CLOSE ? TaskStatus.CANCELLED.name() : null;
        return mapper.resolveException(taskId, version, targetStatus, note.trim());
    }

    public int updateVehicleLifecycle(String vehicleId, String lifecycleStatus) {
        return mapper.updateVehicleLifecycle(vehicleId, lifecycleStatus);
    }

    // ==================== 凭证、附件与异常历史 ====================

    public long insertEvidence(
            String taskId,
            String resultNote,
            BigDecimal arrivalLongitude,
            BigDecimal arrivalLatitude,
            List<String> checklist,
            String removedBatteryId,
            String installedBatteryId,
            List<String> partsUsed,
            BigDecimal targetLongitude,
            BigDecimal targetLatitude,
            String submittedBy,
            String submittedByName
    ) {
        return mapper.insertEvidence(new EvidenceInsert(
                taskId, resultNote.trim(), arrivalLongitude, arrivalLatitude,
                jsonMapper.writeValueAsString(checklist), blankToNull(removedBatteryId),
                blankToNull(installedBatteryId),
                jsonMapper.writeValueAsString(partsUsed == null ? List.of() : partsUsed),
                targetLongitude, targetLatitude, submittedBy, submittedByName));
    }

    public void reviewLatestEvidence(
            String taskId,
            ReviewStatus status,
            String reviewedBy,
            String reviewedByName,
            String note
    ) {
        mapper.reviewLatestEvidence(taskId, status.name(), reviewedBy, reviewedByName, blankToNull(note));
    }

    public long insertAttachment(
            String taskId,
            AttachmentPurpose purpose,
            String originalName,
            String storedName,
            String contentType,
            long sizeBytes,
            String sha256,
            String storagePath,
            String uploadedBy
    ) {
        return mapper.insertAttachment(new AttachmentInsert(
                taskId, purpose.name(), originalName, storedName, contentType,
                sizeBytes, sha256, storagePath, uploadedBy));
    }

    public Optional<StoredAttachment> findAttachment(long attachmentId) {
        return Optional.ofNullable(mapper.findAttachment(attachmentId));
    }

    public List<StoredAttachment> findAttachments(List<Long> attachmentIds) {
        return attachmentIds == null || attachmentIds.isEmpty()
                ? List.of()
                : mapper.findAttachments(attachmentIds);
    }

    /** 输入: 最晚上传时间和批次上限; 输出: 当前事务已锁定且未绑定的附件元数据. */
    public List<StoredAttachment> findUnboundAttachments(Instant uploadedBefore, int limit) {
        return mapper.findUnboundAttachments(uploadedBefore, limit);
    }

    public int deleteUnboundAttachment(long attachmentId) {
        return mapper.deleteUnboundAttachment(attachmentId);
    }

    public void linkEvidenceAttachments(long evidenceId, List<Long> attachmentIds, AttachmentPurpose purpose) {
        if (attachmentIds != null && !attachmentIds.isEmpty()) {
            mapper.linkEvidenceAttachments(evidenceId, attachmentIds, purpose.name());
        }
    }

    public long insertException(
            String taskId,
            ExceptionType type,
            String note,
            String reportedBy,
            String reportedByName
    ) {
        return mapper.insertException(new ExceptionInsert(
                taskId, type.name(), note.trim(), reportedBy, reportedByName));
    }

    public void linkExceptionAttachments(long exceptionId, List<Long> attachmentIds) {
        if (attachmentIds != null && !attachmentIds.isEmpty()) {
            mapper.linkExceptionAttachments(exceptionId, attachmentIds);
        }
    }

    public void resolveLatestException(
            String taskId,
            ExceptionResolutionAction action,
            String note,
            String resolvedBy,
            String resolvedByName
    ) {
        mapper.resolveLatestException(taskId, action.name(), note.trim(), resolvedBy, resolvedByName);
    }

    // ==================== 自动任务触发 ====================

    /** 输入: 规则触发信息; 输出: 新增或聚合触发记录。 */
    public int upsertTrigger(
            String taskId,
            String ruleId,
            String triggerKey,
            Instant occurredAt,
            String payload
    ) {
        return mapper.upsertTrigger(taskId, ruleId, triggerKey, occurredAt, payload);
    }

    public void incrementDuplicateCount(String taskId) {
        mapper.incrementDuplicateCount(taskId);
    }

    /** 输入: 规则、车辆和恢复时间; 输出: 被标记为恢复的触发数量。 */
    public int recoverTriggers(String ruleId, String vehicleId, Instant recoveredAt) {
        return mapper.recoverTriggers(ruleId, vehicleId, recoveredAt);
    }

    public Optional<TaskItem> findRuleTaskWithoutActiveTriggers(String vehicleId) {
        return Optional.ofNullable(mapper.findRuleTaskWithoutActiveTriggers(vehicleId));
    }

    public boolean hasRecentRuleTask(String vehicleId, String ruleId, int cooldownMinutes, Instant occurredAt) {
        return mapper.hasRecentRuleTask(vehicleId, ruleId, occurredAt.minusSeconds(cooldownMinutes * 60L));
    }

    /** 输入: 城市; 输出: 可用于手工扫描规则的车辆最新状态。 */
    public List<AutomationVehicleState> findAutomationVehicleStates(String cityCode, DataPermission permission) {
        return mapper.findAutomationVehicleStates(cityCode, permission).stream().map(this::mapAutomationState).toList();
    }

    public boolean hasGeoViolation(AutomationVehicleState state) {
        return mapper.hasGeoViolation(state.cityCode(), state.rideStatus(), state.longitude(), state.latitude());
    }

    // ==================== 事件与结果映射 ====================

    public void insertEvent(
            String taskId,
            TaskEventType eventType,
            TaskStatus fromStatus,
            TaskStatus toStatus,
            String actorId,
            String actorName,
            String note
    ) {
        mapper.insertEvent(taskId, eventType.name(), enumName(fromStatus), toStatus.name(),
                actorId, actorName, blankToNull(note));
    }

    private List<TaskEvidence> findEvidence(String taskId) {
        return mapper.findEvidence(taskId).stream().map(this::mapEvidence).toList();
    }

    private List<TaskException> findExceptions(String taskId) {
        return mapper.findExceptions(taskId).stream().map(this::mapException).toList();
    }

    private TaskEvidence mapEvidence(EvidenceRow row) {
        return new TaskEvidence(
                row.evidenceId(), row.submissionNo(), row.resultNote(), row.arrivalLongitude(),
                row.arrivalLatitude(), "WGS84", readStringList(row.checklist()), row.removedBatteryId(),
                row.installedBatteryId(), readStringList(row.partsUsed()), row.targetLongitude(),
                row.targetLatitude(), row.reviewStatus(), row.submittedBy(), row.submittedByName(),
                row.submittedAt(), row.reviewedByName(), row.reviewNote(), row.reviewedAt(),
                mapper.findEvidenceAttachments(row.evidenceId()).stream()
                        .map(this::mapEvidenceAttachment).toList());
    }

    private TaskException mapException(ExceptionRow row) {
        return new TaskException(
                row.exceptionId(), row.exceptionType(), row.note(), row.reportedBy(), row.reportedByName(),
                row.reportedAt(), row.resolutionAction(), row.resolutionNote(), row.resolvedByName(),
                row.resolvedAt(), mapper.findExceptionAttachments(row.exceptionId()).stream()
                        .map(this::mapEvidenceAttachment).toList());
    }

    private EvidenceAttachment mapEvidenceAttachment(EvidenceAttachmentRow row) {
        return new EvidenceAttachment(
                row.attachmentId(), row.purpose(), row.originalName(), row.contentType(), row.sizeBytes(),
                "/api/v1/ops/attachments/" + row.attachmentId(), row.uploadedAt());
    }

    private AutomationVehicleState mapAutomationState(AutomationRow row) {
        return new AutomationVehicleState(
                row.vehicleId(), row.orgId(), row.cityCode(), row.areaCode(), row.longitude(), row.latitude(),
                row.batteryPercent(), row.online(), row.controllerStatus(), row.rideStatus(),
                readStringList(row.faultCodes()), row.occurredAt());
    }

    private List<String> readStringList(String json) {
        return json == null ? List.of() : jsonMapper.readValue(json, STRING_LIST_TYPE);
    }

    private String fuzzyOrNull(String value) {
        return value == null || value.isBlank() ? null : "%" + value.trim() + "%";
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }
}

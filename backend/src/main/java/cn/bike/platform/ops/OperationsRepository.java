package cn.bike.platform.ops;

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
import cn.bike.platform.ops.OperationsModels.TaskPriority;
import cn.bike.platform.ops.OperationsModels.TaskSourceType;
import cn.bike.platform.ops.OperationsModels.TaskStatus;
import cn.bike.platform.ops.OperationsModels.TaskSummary;
import cn.bike.platform.ops.OperationsModels.TaskTrigger;
import cn.bike.platform.ops.OperationsModels.TaskType;
import cn.bike.platform.ops.OperationsModels.VehicleSnapshot;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class OperationsRepository {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };
    private static final String TASK_SELECT = """
            SELECT t.*, v.plate_number, o.org_name,
                   assignee.display_name AS assignee_name,
                   creator.display_name AS created_by_name,
                   rule.rule_name, batch.batch_no
            FROM operations_task t
            JOIN vehicle v ON v.vehicle_id = t.vehicle_id
            JOIN organization o ON o.org_id = t.org_id
            LEFT JOIN app_user assignee ON assignee.user_id = t.assignee_id
            LEFT JOIN app_user creator ON creator.user_id = t.created_by
            LEFT JOIN operations_task_rule rule ON rule.rule_id = t.rule_id
            LEFT JOIN operations_task_batch batch ON batch.batch_id = t.batch_id
            """;

    private final JdbcClient jdbcClient;
    private final JsonMapper jsonMapper;

    public OperationsRepository(JdbcClient jdbcClient, JsonMapper jsonMapper) {
        this.jdbcClient = jdbcClient;
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
            String keyword
    ) {
        var query = taskFilters(cityCode, status, type, scope, currentUserId, keyword);
        var sql = TASK_SELECT + query.sql() + """
                 ORDER BY CASE t.priority WHEN 'URGENT' THEN 1 WHEN 'HIGH' THEN 2
                          WHEN 'NORMAL' THEN 3 ELSE 4 END,
                          t.due_at NULLS LAST, t.created_at DESC
                 LIMIT :limit OFFSET :offset
                """;
        query.parameters().put("limit", pageSize);
        query.parameters().put("offset", (page - 1) * pageSize);
        return bind(jdbcClient.sql(sql), query.parameters()).query(this::mapTask).list();
    }

    /** 输入: 任务筛选; 输出: 匹配任务总数。 */
    public long countTasks(
            String cityCode,
            TaskStatus status,
            TaskType type,
            String scope,
            String currentUserId,
            String keyword
    ) {
        var query = taskFilters(cityCode, status, type, scope, currentUserId, keyword);
        return bind(jdbcClient.sql("SELECT count(*) FROM operations_task t" + query.sql()), query.parameters())
                .query(Long.class).single();
    }

    /** 输入: 城市和当前用户; 输出: 队列、验收、异常、超时和个人任务汇总。 */
    public TaskSummary summary(String cityCode, String currentUserId) {
        return jdbcClient.sql("""
                        SELECT count(*) FILTER (WHERE task_status = 'OPEN') AS open_count,
                               count(*) FILTER (WHERE task_status = 'CLAIMED') AS claimed_count,
                               count(*) FILTER (WHERE task_status = 'IN_PROGRESS') AS progress_count,
                               count(*) FILTER (WHERE task_status = 'PENDING_REVIEW') AS review_count,
                               count(*) FILTER (WHERE task_status = 'EXCEPTION') AS exception_count,
                               count(*) FILTER (
                                   WHERE task_status IN ('OPEN', 'CLAIMED', 'IN_PROGRESS', 'PENDING_REVIEW', 'EXCEPTION')
                                     AND due_at < now()
                               ) AS overdue_count,
                               count(*) FILTER (
                                   WHERE task_status = 'COMPLETED'
                                     AND completed_at >= date_trunc('day', now() AT TIME ZONE 'Asia/Shanghai')
                                         AT TIME ZONE 'Asia/Shanghai'
                               ) AS completed_today_count,
                               count(*) FILTER (
                                   WHERE assignee_id = :currentUserId
                                     AND task_status IN ('CLAIMED', 'IN_PROGRESS', 'PENDING_REVIEW', 'EXCEPTION')
                               ) AS my_active_count
                        FROM operations_task WHERE city_code = :cityCode
                        """)
                .param("cityCode", cityCode).param("currentUserId", currentUserId)
                .query((rs, rowNum) -> new TaskSummary(
                        rs.getLong("open_count"), rs.getLong("claimed_count"),
                        rs.getLong("progress_count"), rs.getLong("review_count"),
                        rs.getLong("exception_count"), rs.getLong("overdue_count"),
                        rs.getLong("completed_today_count"), rs.getLong("my_active_count")))
                .single();
    }

    /** 输入: 任务编号; 输出: 包含人员、规则和批次显示名的任务。 */
    public Optional<TaskItem> findTask(String taskId) {
        return jdbcClient.sql(TASK_SELECT + " WHERE t.task_id = :taskId")
                .param("taskId", taskId).query(this::mapTask).optional();
    }

    /** 输入: 多个任务编号; 输出: 保持查询输入顺序的任务列表。 */
    public List<TaskItem> findTasksByIds(List<String> taskIds) {
        if (taskIds.isEmpty()) {
            return List.of();
        }
        return jdbcClient.sql(TASK_SELECT + " WHERE t.task_id IN (:taskIds)")
                .param("taskIds", taskIds).query(this::mapTask).list();
    }

    /** 输入: 车辆编号; 输出: 当前未结束任务。 */
    public Optional<TaskItem> findActiveTaskForVehicle(String vehicleId) {
        return jdbcClient.sql(TASK_SELECT + """
                        WHERE t.vehicle_id = :vehicleId
                          AND t.task_status IN ('OPEN', 'CLAIMED', 'IN_PROGRESS', 'PENDING_REVIEW', 'EXCEPTION')
                        """)
                .param("vehicleId", vehicleId).query(this::mapTask).optional();
    }

    /** 输入: 任务编号; 输出: 任务、时间线、凭证、异常和自动触发记录。 */
    public TaskDetail findTaskDetail(TaskItem task) {
        return new TaskDetail(task, findEvents(task.taskId()), findEvidence(task.taskId()),
                findExceptions(task.taskId()), findTriggers(task.taskId()));
    }

    public List<TaskEvent> findEvents(String taskId) {
        return jdbcClient.sql("""
                        SELECT * FROM operations_task_event
                        WHERE task_id = :taskId ORDER BY created_at, event_id
                        """)
                .param("taskId", taskId).query(this::mapEvent).list();
    }

    /** 输入: 车辆编号; 输出: 创建任务所需的车辆和最新位置快照。 */
    public Optional<VehicleSnapshot> findVehicleSnapshot(String vehicleId) {
        return jdbcClient.sql("""
                        SELECT v.vehicle_id, v.operation_city_code, v.operation_area_code,
                               l.longitude, l.latitude, l.battery_percent
                        FROM vehicle v LEFT JOIN vehicle_latest l ON l.vehicle_id = v.vehicle_id
                        WHERE v.vehicle_id = :vehicleId AND v.lifecycle_status NOT IN ('RETIRED', 'IMPOUNDED')
                        """)
                .param("vehicleId", vehicleId).query((rs, rowNum) -> new VehicleSnapshot(
                        rs.getString("vehicle_id"), rs.getString("operation_city_code"),
                        rs.getString("operation_area_code"), rs.getBigDecimal("longitude"),
                        rs.getBigDecimal("latitude"), nullableInteger(rs, "battery_percent"))).optional();
    }

    /** 输入: 组织编号; 输出: 城市和启用状态快照。 */
    public Optional<OrganizationSnapshot> findOrganization(String orgId) {
        return jdbcClient.sql("SELECT org_id, city_code, status FROM organization WHERE org_id = :orgId")
                .param("orgId", orgId).query((rs, rowNum) -> new OrganizationSnapshot(
                        rs.getString("org_id"), rs.getString("city_code"),
                        "ACTIVE".equals(rs.getString("status")))).optional();
    }

    public Optional<AssigneeOption> findEligibleAssignee(String userId, String cityCode) {
        return jdbcClient.sql("""
                        SELECT u.user_id, u.display_name, u.phone, u.org_id, o.org_name
                        FROM app_user u JOIN organization o ON o.org_id = u.org_id
                        WHERE u.user_id = :userId AND u.role = 'OPERATOR' AND u.status = 'ACTIVE'
                          AND o.status = 'ACTIVE' AND (o.city_code IS NULL OR o.city_code = :cityCode)
                        """)
                .param("userId", userId).param("cityCode", cityCode)
                .query(this::mapAssignee).optional();
    }

    public List<AssigneeOption> findAssignees(String cityCode) {
        return jdbcClient.sql("""
                        SELECT u.user_id, u.display_name, u.phone, u.org_id, o.org_name
                        FROM app_user u JOIN organization o ON o.org_id = u.org_id
                        WHERE u.role = 'OPERATOR' AND u.status = 'ACTIVE' AND o.status = 'ACTIVE'
                          AND (o.city_code IS NULL OR o.city_code = :cityCode)
                        ORDER BY o.org_name, u.display_name
                        """)
                .param("cityCode", cityCode).query(this::mapAssignee).list();
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
        return jdbcClient.sql("""
                        INSERT INTO operations_task (
                            task_id, task_no, task_type, task_status, priority, source_type,
                            title, description, vehicle_id, city_code, area_code, org_id, target_name,
                            source_longitude, source_latitude, battery_percent, assignee_id, created_by,
                            rule_id, batch_id, trigger_key, due_at, claimed_at
                        ) VALUES (
                            :taskId, :taskNo, :taskType, :taskStatus, :priority, :sourceType,
                            :title, :description, :vehicleId, :cityCode, :areaCode, :orgId, :targetName,
                            :longitude, :latitude, :batteryPercent, :assigneeId, :createdBy,
                            :ruleId, :batchId, :triggerKey, :dueAt,
                            CASE WHEN CAST(:assigneeId AS VARCHAR) IS NULL THEN NULL ELSE now() END
                        ) ON CONFLICT DO NOTHING
                        """)
                .param("taskId", taskId).param("taskNo", taskNo)
                .param("taskType", request.taskType().name()).param("taskStatus", status.name())
                .param("priority", request.priority().name()).param("sourceType", sourceType.name())
                .param("title", request.title().trim()).param("description", blankToNull(request.description()))
                .param("vehicleId", vehicle.vehicleId()).param("cityCode", vehicle.cityCode())
                .param("areaCode", vehicle.areaCode()).param("orgId", request.orgId())
                .param("targetName", blankToNull(request.targetName())).param("longitude", vehicle.longitude())
                .param("latitude", vehicle.latitude()).param("batteryPercent", vehicle.batteryPercent())
                .param("assigneeId", assigneeId).param("createdBy", createdBy).param("ruleId", ruleId)
                .param("batchId", batchId).param("triggerKey", triggerKey)
                .param("dueAt", request.dueAt() == null ? null : Timestamp.from(request.dueAt())).update();
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
        jdbcClient.sql("""
                        INSERT INTO operations_task_batch (
                            batch_id, batch_no, batch_name, city_code, org_id, task_type,
                            requested_count, created_by
                        ) VALUES (
                            :batchId, :batchNo, :batchName, :cityCode, :orgId, :taskType,
                            :requestedCount, :createdBy
                        )
                        """)
                .param("batchId", batchId).param("batchNo", batchNo).param("batchName", batchName.trim())
                .param("cityCode", cityCode).param("orgId", orgId).param("taskType", taskType.name())
                .param("requestedCount", requestedCount).param("createdBy", createdBy).update();
    }

    public void updateBatchCounts(String batchId, int createdCount, int skippedCount) {
        jdbcClient.sql("""
                        UPDATE operations_task_batch SET created_count = :createdCount,
                            skipped_count = :skippedCount WHERE batch_id = :batchId
                        """)
                .param("batchId", batchId).param("createdCount", createdCount)
                .param("skippedCount", skippedCount).update();
    }

    // ==================== 状态流转 ====================

    public int claim(String taskId, String assigneeId) {
        return jdbcClient.sql("""
                        UPDATE operations_task SET task_status = 'CLAIMED', assignee_id = :assigneeId,
                            claimed_at = now(), updated_at = now(), version = version + 1
                        WHERE task_id = :taskId AND task_status = 'OPEN' AND assignee_id IS NULL
                        """).param("taskId", taskId).param("assigneeId", assigneeId).update();
    }

    public int assign(String taskId, int version, String assigneeId) {
        return jdbcClient.sql("""
                        UPDATE operations_task SET task_status = 'CLAIMED', assignee_id = :assigneeId,
                            claimed_at = now(), updated_at = now(), version = version + 1
                        WHERE task_id = :taskId AND version = :version
                          AND task_status IN ('OPEN', 'CLAIMED')
                        """).param("taskId", taskId).param("version", version)
                .param("assigneeId", assigneeId).update();
    }

    public int release(String taskId, int version, String assigneeId) {
        return jdbcClient.sql("""
                        UPDATE operations_task SET task_status = 'OPEN', assignee_id = NULL, claimed_at = NULL,
                            updated_at = now(), version = version + 1
                        WHERE task_id = :taskId AND version = :version
                          AND task_status = 'CLAIMED' AND assignee_id = :assigneeId
                        """).param("taskId", taskId).param("version", version)
                .param("assigneeId", assigneeId).update();
    }

    public int start(String taskId, int version, String assigneeId) {
        return jdbcClient.sql("""
                        UPDATE operations_task SET task_status = 'IN_PROGRESS', started_at = now(),
                            updated_at = now(), version = version + 1
                        WHERE task_id = :taskId AND version = :version
                          AND task_status = 'CLAIMED' AND assignee_id = :assigneeId
                        """).param("taskId", taskId).param("version", version)
                .param("assigneeId", assigneeId).update();
    }

    public int submitForReview(String taskId, int version, String assigneeId, String resultNote) {
        return jdbcClient.sql("""
                        UPDATE operations_task SET task_status = 'PENDING_REVIEW', result_note = :resultNote,
                            submitted_at = now(), updated_at = now(), version = version + 1
                        WHERE task_id = :taskId AND version = :version
                          AND task_status = 'IN_PROGRESS' AND assignee_id = :assigneeId
                        """).param("taskId", taskId).param("version", version)
                .param("assigneeId", assigneeId).param("resultNote", resultNote.trim()).update();
    }

    public int approve(String taskId, int version) {
        return jdbcClient.sql("""
                        UPDATE operations_task SET task_status = 'COMPLETED', completed_at = now(),
                            updated_at = now(), version = version + 1
                        WHERE task_id = :taskId AND version = :version AND task_status = 'PENDING_REVIEW'
                        """).param("taskId", taskId).param("version", version).update();
    }

    public int reject(String taskId, int version) {
        return jdbcClient.sql("""
                        UPDATE operations_task SET task_status = 'IN_PROGRESS', submitted_at = NULL,
                            updated_at = now(), version = version + 1
                        WHERE task_id = :taskId AND version = :version AND task_status = 'PENDING_REVIEW'
                        """).param("taskId", taskId).param("version", version).update();
    }

    public int cancel(String taskId, int version, String reason) {
        return jdbcClient.sql("""
                        UPDATE operations_task SET task_status = 'CANCELLED', result_note = :reason,
                            completed_at = now(), updated_at = now(), version = version + 1
                        WHERE task_id = :taskId AND version = :version
                          AND task_status IN ('OPEN', 'CLAIMED', 'IN_PROGRESS', 'PENDING_REVIEW', 'EXCEPTION')
                        """).param("taskId", taskId).param("version", version)
                .param("reason", reason.trim()).update();
    }

    public int reportException(
            String taskId,
            int version,
            String assigneeId,
            ExceptionType type,
            String note
    ) {
        return jdbcClient.sql("""
                        UPDATE operations_task SET task_status = 'EXCEPTION', exception_type = :type,
                            exception_note = :note, exception_from_status = task_status,
                            exception_at = now(), updated_at = now(), version = version + 1
                        WHERE task_id = :taskId AND version = :version AND assignee_id = :assigneeId
                          AND task_status IN ('CLAIMED', 'IN_PROGRESS')
                        """).param("taskId", taskId).param("version", version)
                .param("assigneeId", assigneeId).param("type", type.name()).param("note", note.trim()).update();
    }

    public int resolveException(String taskId, int version, ExceptionResolutionAction action, String note) {
        var targetStatus = action == ExceptionResolutionAction.CLOSE ? "CANCELLED" : null;
        return jdbcClient.sql("""
                        UPDATE operations_task SET
                            task_status = CASE WHEN CAST(:targetStatus AS VARCHAR) IS NOT NULL THEN :targetStatus
                                WHEN exception_from_status = 'IN_PROGRESS' THEN 'IN_PROGRESS'
                                WHEN assignee_id IS NOT NULL THEN 'CLAIMED' ELSE 'OPEN' END,
                            result_note = CASE WHEN CAST(:targetStatus AS VARCHAR) IS NOT NULL THEN :note ELSE result_note END,
                            completed_at = CASE WHEN CAST(:targetStatus AS VARCHAR) IS NOT NULL THEN now() ELSE completed_at END,
                            exception_type = NULL, exception_note = NULL, exception_from_status = NULL,
                            exception_at = NULL, updated_at = now(), version = version + 1
                        WHERE task_id = :taskId AND version = :version AND task_status = 'EXCEPTION'
                        """).param("taskId", taskId).param("version", version)
                .param("targetStatus", targetStatus).param("note", note.trim()).update();
    }

    public int updateVehicleLifecycle(String vehicleId, String lifecycleStatus) {
        return jdbcClient.sql("""
                        UPDATE vehicle SET lifecycle_status = :status, updated_at = now()
                        WHERE vehicle_id = :vehicleId AND lifecycle_status NOT IN ('RETIRED', 'IMPOUNDED')
                        """).param("vehicleId", vehicleId).param("status", lifecycleStatus).update();
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
        return jdbcClient.sql("""
                        INSERT INTO operations_task_evidence (
                            task_id, submission_no, result_note, arrival_longitude, arrival_latitude,
                            checklist, removed_battery_id, installed_battery_id, parts_used,
                            target_longitude, target_latitude, submitted_by, submitted_by_name
                        ) VALUES (
                            :taskId, (SELECT coalesce(max(submission_no), 0) + 1
                                      FROM operations_task_evidence WHERE task_id = :taskId),
                            :resultNote, :arrivalLongitude, :arrivalLatitude, CAST(:checklist AS jsonb),
                            :removedBatteryId, :installedBatteryId, CAST(:partsUsed AS jsonb),
                            :targetLongitude, :targetLatitude, :submittedBy, :submittedByName
                        ) RETURNING evidence_id
                        """).param("taskId", taskId).param("resultNote", resultNote.trim())
                .param("arrivalLongitude", arrivalLongitude).param("arrivalLatitude", arrivalLatitude)
                .param("checklist", jsonMapper.writeValueAsString(checklist))
                .param("removedBatteryId", blankToNull(removedBatteryId))
                .param("installedBatteryId", blankToNull(installedBatteryId))
                .param("partsUsed", jsonMapper.writeValueAsString(partsUsed == null ? List.of() : partsUsed))
                .param("targetLongitude", targetLongitude).param("targetLatitude", targetLatitude)
                .param("submittedBy", submittedBy).param("submittedByName", submittedByName)
                .query(Long.class).single();
    }

    public void reviewLatestEvidence(
            String taskId,
            ReviewStatus status,
            String reviewedBy,
            String reviewedByName,
            String note
    ) {
        jdbcClient.sql("""
                        UPDATE operations_task_evidence SET review_status = :status,
                            reviewed_by = :reviewedBy, reviewed_by_name = :reviewedByName,
                            review_note = :note, reviewed_at = now()
                        WHERE evidence_id = (
                            SELECT evidence_id FROM operations_task_evidence
                            WHERE task_id = :taskId ORDER BY submission_no DESC LIMIT 1
                        ) AND review_status = 'PENDING'
                        """).param("taskId", taskId).param("status", status.name())
                .param("reviewedBy", reviewedBy).param("reviewedByName", reviewedByName)
                .param("note", blankToNull(note)).update();
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
        return jdbcClient.sql("""
                        INSERT INTO operations_task_attachment (
                            task_id, purpose, original_name, stored_name, content_type,
                            size_bytes, sha256, storage_path, uploaded_by
                        ) VALUES (
                            :taskId, :purpose, :originalName, :storedName, :contentType,
                            :sizeBytes, :sha256, :storagePath, :uploadedBy
                        ) RETURNING attachment_id
                        """).param("taskId", taskId).param("purpose", purpose.name())
                .param("originalName", originalName).param("storedName", storedName)
                .param("contentType", contentType).param("sizeBytes", sizeBytes)
                .param("sha256", sha256)
                .param("storagePath", storagePath).param("uploadedBy", uploadedBy)
                .query(Long.class).single();
    }

    public Optional<StoredAttachment> findAttachment(long attachmentId) {
        return jdbcClient.sql("SELECT * FROM operations_task_attachment WHERE attachment_id = :attachmentId")
                .param("attachmentId", attachmentId).query(this::mapStoredAttachment).optional();
    }

    public List<StoredAttachment> findAttachments(List<Long> attachmentIds) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return List.of();
        }
        return jdbcClient.sql("""
                        SELECT * FROM operations_task_attachment WHERE attachment_id IN (:attachmentIds)
                        """).param("attachmentIds", attachmentIds).query(this::mapStoredAttachment).list();
    }

    public void linkEvidenceAttachments(long evidenceId, List<Long> attachmentIds, AttachmentPurpose purpose) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return;
        }
        jdbcClient.sql("""
                        INSERT INTO operations_task_evidence_attachment (evidence_id, attachment_id, purpose)
                        SELECT :evidenceId, attachment_id, :purpose FROM operations_task_attachment
                        WHERE attachment_id IN (:attachmentIds)
                        """).param("evidenceId", evidenceId).param("attachmentIds", attachmentIds)
                .param("purpose", purpose.name()).update();
    }

    public long insertException(
            String taskId,
            ExceptionType type,
            String note,
            String reportedBy,
            String reportedByName
    ) {
        return jdbcClient.sql("""
                        INSERT INTO operations_task_exception (
                            task_id, exception_type, note, reported_by, reported_by_name
                        ) VALUES (:taskId, :type, :note, :reportedBy, :reportedByName)
                        RETURNING exception_id
                        """).param("taskId", taskId).param("type", type.name()).param("note", note.trim())
                .param("reportedBy", reportedBy).param("reportedByName", reportedByName)
                .query(Long.class).single();
    }

    public void linkExceptionAttachments(long exceptionId, List<Long> attachmentIds) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return;
        }
        jdbcClient.sql("""
                        INSERT INTO operations_task_exception_attachment (exception_id, attachment_id)
                        SELECT :exceptionId, attachment_id FROM operations_task_attachment
                        WHERE attachment_id IN (:attachmentIds)
                        """).param("exceptionId", exceptionId).param("attachmentIds", attachmentIds).update();
    }

    public void resolveLatestException(
            String taskId,
            ExceptionResolutionAction action,
            String note,
            String resolvedBy,
            String resolvedByName
    ) {
        jdbcClient.sql("""
                        UPDATE operations_task_exception SET resolution_action = :action,
                            resolution_note = :note, resolved_by = :resolvedBy,
                            resolved_by_name = :resolvedByName, resolved_at = now()
                        WHERE exception_id = (
                            SELECT exception_id FROM operations_task_exception
                            WHERE task_id = :taskId AND resolved_at IS NULL
                            ORDER BY reported_at DESC LIMIT 1
                        )
                        """).param("taskId", taskId).param("action", action.name()).param("note", note.trim())
                .param("resolvedBy", resolvedBy).param("resolvedByName", resolvedByName).update();
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
        return jdbcClient.sql("""
                        INSERT INTO operations_task_trigger (
                            task_id, rule_id, trigger_key, first_triggered_at,
                            last_triggered_at, last_payload
                        ) VALUES (
                            :taskId, :ruleId, :triggerKey, :occurredAt, :occurredAt, CAST(:payload AS jsonb)
                        ) ON CONFLICT (task_id, rule_id, trigger_key) DO UPDATE SET
                            active = true,
                            occurrence_count = operations_task_trigger.occurrence_count + 1,
                            last_triggered_at = GREATEST(operations_task_trigger.last_triggered_at, EXCLUDED.last_triggered_at),
                            recovered_at = NULL,
                            last_payload = EXCLUDED.last_payload
                        RETURNING occurrence_count
                        """).param("taskId", taskId).param("ruleId", ruleId).param("triggerKey", triggerKey)
                .param("occurredAt", Timestamp.from(occurredAt)).param("payload", payload)
                .query(Integer.class).single();
    }

    public void incrementDuplicateCount(String taskId) {
        jdbcClient.sql("""
                        UPDATE operations_task SET duplicate_count = duplicate_count + 1,
                            updated_at = now() WHERE task_id = :taskId
                        """).param("taskId", taskId).update();
    }

    /** 输入: 规则、车辆和恢复时间; 输出: 被标记为恢复的触发数量。 */
    public int recoverTriggers(String ruleId, String vehicleId, Instant recoveredAt) {
        return jdbcClient.sql("""
                        UPDATE operations_task_trigger tr SET active = false, recovered_at = :recoveredAt
                        FROM operations_task task
                        WHERE tr.task_id = task.task_id AND tr.rule_id = :ruleId
                          AND task.vehicle_id = :vehicleId AND tr.active = true
                        """).param("ruleId", ruleId).param("vehicleId", vehicleId)
                .param("recoveredAt", Timestamp.from(recoveredAt)).update();
    }

    public Optional<TaskItem> findRuleTaskWithoutActiveTriggers(String vehicleId) {
        return jdbcClient.sql(TASK_SELECT + """
                        WHERE t.vehicle_id = :vehicleId AND t.source_type = 'RULE'
                          AND t.task_status IN ('OPEN', 'CLAIMED')
                          AND NOT EXISTS (
                              SELECT 1 FROM operations_task_trigger tr
                              WHERE tr.task_id = t.task_id AND tr.active = true
                          )
                        ORDER BY t.created_at DESC LIMIT 1
                        """).param("vehicleId", vehicleId).query(this::mapTask).optional();
    }

    public boolean hasRecentRuleTask(String vehicleId, String ruleId, int cooldownMinutes, Instant occurredAt) {
        return jdbcClient.sql("""
                        SELECT EXISTS (
                            SELECT 1 FROM operations_task
                            WHERE vehicle_id = :vehicleId AND rule_id = :ruleId
                              AND created_at >= :cutoff
                        )
                        """).param("vehicleId", vehicleId).param("ruleId", ruleId)
                .param("cutoff", Timestamp.from(occurredAt.minusSeconds(cooldownMinutes * 60L)))
                .query(Boolean.class).single();
    }

    /** 输入: 城市; 输出: 可用于手工扫描规则的车辆最新状态。 */
    public List<AutomationVehicleState> findAutomationVehicleStates(String cityCode) {
        return jdbcClient.sql("""
                        SELECT v.vehicle_id, v.operation_city_code, v.operation_area_code,
                               l.longitude, l.latitude, l.battery_percent, l.online,
                               l.controller_status, l.ride_status, l.fault_codes::text, l.reported_at
                        FROM vehicle v JOIN vehicle_latest l ON l.vehicle_id = v.vehicle_id
                        WHERE v.operation_city_code = :cityCode
                          AND v.lifecycle_status NOT IN ('RETIRED', 'IMPOUNDED')
                        """).param("cityCode", cityCode).query(this::mapAutomationState).list();
    }

    public boolean hasGeoViolation(AutomationVehicleState state) {
        return jdbcClient.sql("""
                        SELECT EXISTS (
                            SELECT 1 FROM geofence f
                            WHERE f.city_code = :cityCode AND f.status = 'ACTIVE'
                              AND f.fence_type = 'NO_PARK' AND :rideStatus = 'IDLE'
                              AND ST_Covers(f.boundary, ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326))
                        ) OR (
                            EXISTS (
                                SELECT 1 FROM geofence f WHERE f.city_code = :cityCode
                                  AND f.status = 'ACTIVE' AND f.fence_type = 'OPERATION'
                            ) AND NOT EXISTS (
                                SELECT 1 FROM geofence f WHERE f.city_code = :cityCode
                                  AND f.status = 'ACTIVE' AND f.fence_type = 'OPERATION'
                                  AND ST_Covers(f.boundary,
                                      ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326))
                            )
                        )
                        """).param("cityCode", state.cityCode()).param("rideStatus", state.rideStatus())
                .param("longitude", state.longitude()).param("latitude", state.latitude())
                .query(Boolean.class).single();
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
        jdbcClient.sql("""
                        INSERT INTO operations_task_event (
                            task_id, event_type, from_status, to_status, actor_id, actor_name, note
                        ) VALUES (
                            :taskId, :eventType, :fromStatus, :toStatus, :actorId, :actorName, :note
                        )
                        """).param("taskId", taskId).param("eventType", eventType.name())
                .param("fromStatus", fromStatus == null ? null : fromStatus.name())
                .param("toStatus", toStatus.name()).param("actorId", actorId)
                .param("actorName", actorName).param("note", blankToNull(note)).update();
    }

    private List<TaskEvidence> findEvidence(String taskId) {
        return jdbcClient.sql("""
                        SELECT * FROM operations_task_evidence
                        WHERE task_id = :taskId ORDER BY submission_no DESC
                        """).param("taskId", taskId).query((rs, rowNum) -> new TaskEvidence(
                        rs.getLong("evidence_id"), rs.getInt("submission_no"), rs.getString("result_note"),
                        rs.getBigDecimal("arrival_longitude"), rs.getBigDecimal("arrival_latitude"),
                        readStringList(rs.getString("checklist")), rs.getString("removed_battery_id"),
                        rs.getString("installed_battery_id"), readStringList(rs.getString("parts_used")),
                        rs.getBigDecimal("target_longitude"), rs.getBigDecimal("target_latitude"),
                        ReviewStatus.valueOf(rs.getString("review_status")), rs.getString("submitted_by"),
                        rs.getString("submitted_by_name"), instant(rs, "submitted_at"),
                        rs.getString("reviewed_by_name"), rs.getString("review_note"),
                        instant(rs, "reviewed_at"), findEvidenceAttachments(rs.getLong("evidence_id"))
                )).list();
    }

    private List<TaskException> findExceptions(String taskId) {
        return jdbcClient.sql("""
                        SELECT * FROM operations_task_exception
                        WHERE task_id = :taskId ORDER BY reported_at DESC
                        """).param("taskId", taskId).query((rs, rowNum) -> {
                    var action = rs.getString("resolution_action");
                    return new TaskException(rs.getLong("exception_id"),
                            ExceptionType.valueOf(rs.getString("exception_type")), rs.getString("note"),
                            rs.getString("reported_by"), rs.getString("reported_by_name"),
                            instant(rs, "reported_at"), action == null ? null : ExceptionResolutionAction.valueOf(action),
                            rs.getString("resolution_note"), rs.getString("resolved_by_name"),
                            instant(rs, "resolved_at"), findExceptionAttachments(rs.getLong("exception_id")));
                }).list();
    }

    private List<TaskTrigger> findTriggers(String taskId) {
        return jdbcClient.sql("""
                        SELECT tr.*, rule.rule_name FROM operations_task_trigger tr
                        JOIN operations_task_rule rule ON rule.rule_id = tr.rule_id
                        WHERE tr.task_id = :taskId ORDER BY tr.first_triggered_at
                        """).param("taskId", taskId).query((rs, rowNum) -> new TaskTrigger(
                        rs.getLong("trigger_id"), rs.getString("rule_id"), rs.getString("rule_name"),
                        rs.getString("trigger_key"), rs.getBoolean("active"), rs.getInt("occurrence_count"),
                        instant(rs, "first_triggered_at"), instant(rs, "last_triggered_at"),
                        instant(rs, "recovered_at"))).list();
    }

    private List<EvidenceAttachment> findEvidenceAttachments(long evidenceId) {
        return jdbcClient.sql("""
                        SELECT a.* FROM operations_task_attachment a
                        JOIN operations_task_evidence_attachment link ON link.attachment_id = a.attachment_id
                        WHERE link.evidence_id = :evidenceId ORDER BY a.uploaded_at
                        """).param("evidenceId", evidenceId).query(this::mapEvidenceAttachment).list();
    }

    private List<EvidenceAttachment> findExceptionAttachments(long exceptionId) {
        return jdbcClient.sql("""
                        SELECT a.* FROM operations_task_attachment a
                        JOIN operations_task_exception_attachment link ON link.attachment_id = a.attachment_id
                        WHERE link.exception_id = :exceptionId ORDER BY a.uploaded_at
                        """).param("exceptionId", exceptionId).query(this::mapEvidenceAttachment).list();
    }

    private Query taskFilters(
            String cityCode,
            TaskStatus status,
            TaskType type,
            String scope,
            String currentUserId,
            String keyword
    ) {
        var where = new StringBuilder(" WHERE t.city_code = :cityCode");
        var parameters = new LinkedHashMap<String, Object>();
        parameters.put("cityCode", cityCode);
        if (status != null) {
            where.append(" AND t.task_status = :status");
            parameters.put("status", status.name());
        }
        if (type != null) {
            where.append(" AND t.task_type = :type");
            parameters.put("type", type.name());
        }
        if ("MINE".equals(scope)) {
            where.append(" AND t.assignee_id = :currentUserId");
            parameters.put("currentUserId", currentUserId);
        } else if ("UNASSIGNED".equals(scope)) {
            where.append(" AND t.task_status = 'OPEN' AND t.assignee_id IS NULL");
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (t.task_no ILIKE :keyword OR t.vehicle_id ILIKE :keyword OR t.title ILIKE :keyword)");
            parameters.put("keyword", "%" + keyword.trim() + "%");
        }
        return new Query(where.toString(), parameters);
    }

    private JdbcClient.StatementSpec bind(JdbcClient.StatementSpec statement, Map<String, Object> parameters) {
        parameters.forEach(statement::param);
        return statement;
    }

    private TaskItem mapTask(ResultSet rs, int rowNum) throws SQLException {
        var exceptionType = rs.getString("exception_type");
        return new TaskItem(
                rs.getString("task_id"), rs.getString("task_no"), TaskType.valueOf(rs.getString("task_type")),
                TaskStatus.valueOf(rs.getString("task_status")), TaskPriority.valueOf(rs.getString("priority")),
                TaskSourceType.valueOf(rs.getString("source_type")), rs.getString("title"),
                rs.getString("description"), rs.getString("vehicle_id"), rs.getString("plate_number"),
                rs.getString("city_code"), rs.getString("area_code"), rs.getString("org_id"),
                rs.getString("org_name"), rs.getString("target_name"), rs.getBigDecimal("source_longitude"),
                rs.getBigDecimal("source_latitude"), nullableInteger(rs, "battery_percent"),
                rs.getString("assignee_id"), rs.getString("assignee_name"), rs.getString("created_by"),
                rs.getString("created_by_name"), rs.getString("rule_id"), rs.getString("rule_name"),
                rs.getString("batch_id"), rs.getString("batch_no"), rs.getString("trigger_key"),
                rs.getInt("duplicate_count"), instant(rs, "due_at"), instant(rs, "claimed_at"),
                instant(rs, "started_at"), instant(rs, "submitted_at"), instant(rs, "completed_at"),
                rs.getString("result_note"), exceptionType == null ? null : ExceptionType.valueOf(exceptionType),
                rs.getString("exception_note"), instant(rs, "exception_at"), rs.getInt("version"),
                instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    private TaskEvent mapEvent(ResultSet rs, int rowNum) throws SQLException {
        var from = rs.getString("from_status");
        return new TaskEvent(rs.getLong("event_id"), TaskEventType.valueOf(rs.getString("event_type")),
                from == null ? null : TaskStatus.valueOf(from), TaskStatus.valueOf(rs.getString("to_status")),
                rs.getString("actor_id"), rs.getString("actor_name"), rs.getString("note"),
                instant(rs, "created_at"));
    }

    private AssigneeOption mapAssignee(ResultSet rs, int rowNum) throws SQLException {
        return new AssigneeOption(rs.getString("user_id"), rs.getString("display_name"),
                rs.getString("phone"), rs.getString("org_id"), rs.getString("org_name"));
    }

    private EvidenceAttachment mapEvidenceAttachment(ResultSet rs, int rowNum) throws SQLException {
        var id = rs.getLong("attachment_id");
        return new EvidenceAttachment(id, AttachmentPurpose.valueOf(rs.getString("purpose")),
                rs.getString("original_name"), rs.getString("content_type"), rs.getLong("size_bytes"),
                "/api/v1/ops/attachments/" + id, instant(rs, "uploaded_at"));
    }

    private StoredAttachment mapStoredAttachment(ResultSet rs, int rowNum) throws SQLException {
        return new StoredAttachment(rs.getLong("attachment_id"), rs.getString("task_id"),
                AttachmentPurpose.valueOf(rs.getString("purpose")), rs.getString("original_name"),
                rs.getString("stored_name"), rs.getString("content_type"), rs.getLong("size_bytes"),
                rs.getString("sha256"), rs.getString("storage_path"), rs.getString("uploaded_by"),
                instant(rs, "uploaded_at"));
    }

    private AutomationVehicleState mapAutomationState(ResultSet rs, int rowNum) throws SQLException {
        return new AutomationVehicleState(rs.getString("vehicle_id"), rs.getString("operation_city_code"),
                rs.getString("operation_area_code"), rs.getBigDecimal("longitude"),
                rs.getBigDecimal("latitude"), nullableInteger(rs, "battery_percent"), rs.getBoolean("online"),
                rs.getString("controller_status"), rs.getString("ride_status"),
                readStringList(rs.getString("fault_codes")), instant(rs, "reported_at"));
    }

    private List<String> readStringList(String json) {
        return json == null ? List.of() : jsonMapper.readValue(json, STRING_LIST_TYPE);
    }

    private Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        var value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record Query(String sql, LinkedHashMap<String, Object> parameters) {
    }
}

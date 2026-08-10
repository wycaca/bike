package cn.bike.platform.ops;

import cn.bike.platform.ops.OperationsModels.AssigneeOption;
import cn.bike.platform.ops.OperationsModels.CreateTaskRequest;
import cn.bike.platform.ops.OperationsModels.OrganizationSnapshot;
import cn.bike.platform.ops.OperationsModels.TaskEvent;
import cn.bike.platform.ops.OperationsModels.TaskEventType;
import cn.bike.platform.ops.OperationsModels.TaskItem;
import cn.bike.platform.ops.OperationsModels.TaskPriority;
import cn.bike.platform.ops.OperationsModels.TaskStatus;
import cn.bike.platform.ops.OperationsModels.TaskSummary;
import cn.bike.platform.ops.OperationsModels.TaskType;
import cn.bike.platform.ops.OperationsModels.VehicleSnapshot;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

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

    private static final String TASK_SELECT = """
            SELECT t.*, v.plate_number, o.org_name,
                   assignee.display_name AS assignee_name,
                   creator.display_name AS created_by_name
            FROM operations_task t
            JOIN vehicle v ON v.vehicle_id = t.vehicle_id
            JOIN organization o ON o.org_id = t.org_id
            LEFT JOIN app_user assignee ON assignee.user_id = t.assignee_id
            JOIN app_user creator ON creator.user_id = t.created_by
            """;

    private final JdbcClient jdbcClient;

    public OperationsRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
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

    /** 输入: 城市和当前用户; 输出: 队列、超时、当日完成和我的任务汇总。 */
    public TaskSummary summary(String cityCode, String currentUserId) {
        return jdbcClient.sql("""
                        SELECT count(*) FILTER (WHERE task_status = 'OPEN') AS open_count,
                               count(*) FILTER (WHERE task_status = 'CLAIMED') AS claimed_count,
                               count(*) FILTER (WHERE task_status = 'IN_PROGRESS') AS progress_count,
                               count(*) FILTER (
                                   WHERE task_status IN ('OPEN', 'CLAIMED', 'IN_PROGRESS') AND due_at < now()
                               ) AS overdue_count,
                               count(*) FILTER (
                                   WHERE task_status = 'COMPLETED'
                                     AND completed_at >= date_trunc('day', now() AT TIME ZONE 'Asia/Shanghai')
                                         AT TIME ZONE 'Asia/Shanghai'
                               ) AS completed_today_count,
                               count(*) FILTER (
                                   WHERE assignee_id = :currentUserId
                                     AND task_status IN ('CLAIMED', 'IN_PROGRESS')
                               ) AS my_active_count
                        FROM operations_task WHERE city_code = :cityCode
                        """)
                .param("cityCode", cityCode).param("currentUserId", currentUserId)
                .query((rs, rowNum) -> new TaskSummary(
                        rs.getLong("open_count"), rs.getLong("claimed_count"),
                        rs.getLong("progress_count"), rs.getLong("overdue_count"),
                        rs.getLong("completed_today_count"), rs.getLong("my_active_count")))
                .single();
    }

    /** 输入: 任务编号; 输出: 包含人员和组织显示名的任务。 */
    public Optional<TaskItem> findTask(String taskId) {
        return jdbcClient.sql(TASK_SELECT + " WHERE t.task_id = :taskId")
                .param("taskId", taskId).query(this::mapTask).optional();
    }

    /** 输入: 任务编号; 输出: 按发生顺序排列的任务事件。 */
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
                .param("vehicleId", vehicleId)
                .query((rs, rowNum) -> new VehicleSnapshot(
                        rs.getString("vehicle_id"), rs.getString("operation_city_code"),
                        rs.getString("operation_area_code"), rs.getBigDecimal("longitude"),
                        rs.getBigDecimal("latitude"), nullableInteger(rs, "battery_percent")))
                .optional();
    }

    /** 输入: 组织编号; 输出: 城市和启用状态快照。 */
    public Optional<OrganizationSnapshot> findOrganization(String orgId) {
        return jdbcClient.sql("SELECT org_id, city_code, status FROM organization WHERE org_id = :orgId")
                .param("orgId", orgId)
                .query((rs, rowNum) -> new OrganizationSnapshot(
                        rs.getString("org_id"), rs.getString("city_code"),
                        "ACTIVE".equals(rs.getString("status"))))
                .optional();
    }

    /** 输入: 用户编号和任务城市; 输出: 可执行现场任务的启用运营人员。 */
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

    /** 输入: 城市; 输出: 可由管理员指派的启用运营人员。 */
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

    // ==================== 任务写入与状态流转 ====================

    /** 输入: 任务标识、请求、车辆快照和创建者; 输出: 新建的等待或已指派任务。 */
    public void insertTask(
            String taskId,
            String taskNo,
            CreateTaskRequest request,
            VehicleSnapshot vehicle,
            String assigneeId,
            String createdBy
    ) {
        var status = assigneeId == null ? TaskStatus.OPEN : TaskStatus.CLAIMED;
        jdbcClient.sql("""
                        INSERT INTO operations_task (
                            task_id, task_no, task_type, task_status, priority, title, description,
                            vehicle_id, city_code, area_code, org_id, target_name,
                            source_longitude, source_latitude, battery_percent,
                            assignee_id, created_by, due_at, claimed_at
                        ) VALUES (
                            :taskId, :taskNo, :taskType, :taskStatus, :priority, :title, :description,
                            :vehicleId, :cityCode, :areaCode, :orgId, :targetName,
                            :longitude, :latitude, :batteryPercent,
                            :assigneeId, :createdBy, :dueAt,
                            CASE WHEN CAST(:assigneeId AS VARCHAR) IS NULL THEN NULL ELSE now() END
                        )
                        """)
                .param("taskId", taskId).param("taskNo", taskNo)
                .param("taskType", request.taskType().name()).param("taskStatus", status.name())
                .param("priority", request.priority().name()).param("title", request.title().trim())
                .param("description", blankToNull(request.description())).param("vehicleId", vehicle.vehicleId())
                .param("cityCode", vehicle.cityCode()).param("areaCode", vehicle.areaCode())
                .param("orgId", request.orgId()).param("targetName", blankToNull(request.targetName()))
                .param("longitude", vehicle.longitude()).param("latitude", vehicle.latitude())
                .param("batteryPercent", vehicle.batteryPercent()).param("assigneeId", assigneeId)
                .param("createdBy", createdBy)
                .param("dueAt", request.dueAt() == null ? null : Timestamp.from(request.dueAt()))
                .update();
    }

    /** 输入: 抢单人和任务编号; 输出: 仅在未领取时成功更新的行数。 */
    public int claim(String taskId, String assigneeId) {
        return jdbcClient.sql("""
                        UPDATE operations_task
                        SET task_status = 'CLAIMED', assignee_id = :assigneeId,
                            claimed_at = now(), updated_at = now(), version = version + 1
                        WHERE task_id = :taskId AND task_status = 'OPEN' AND assignee_id IS NULL
                        """)
                .param("taskId", taskId).param("assigneeId", assigneeId).update();
    }

    /** 输入: 当前版本和目标人员; 输出: 管理员指派或改派成功的行数。 */
    public int assign(String taskId, int version, String assigneeId) {
        return jdbcClient.sql("""
                        UPDATE operations_task
                        SET task_status = 'CLAIMED', assignee_id = :assigneeId,
                            claimed_at = now(), updated_at = now(), version = version + 1
                        WHERE task_id = :taskId AND version = :version
                          AND task_status IN ('OPEN', 'CLAIMED')
                        """)
                .param("taskId", taskId).param("version", version)
                .param("assigneeId", assigneeId).update();
    }

    /** 输入: 当前版本和领取人; 输出: 释放回公共任务池的行数。 */
    public int release(String taskId, int version, String assigneeId) {
        return jdbcClient.sql("""
                        UPDATE operations_task
                        SET task_status = 'OPEN', assignee_id = NULL, claimed_at = NULL,
                            updated_at = now(), version = version + 1
                        WHERE task_id = :taskId AND version = :version
                          AND task_status = 'CLAIMED' AND assignee_id = :assigneeId
                        """)
                .param("taskId", taskId).param("version", version)
                .param("assigneeId", assigneeId).update();
    }

    /** 输入: 当前版本和领取人; 输出: 开始执行任务的行数。 */
    public int start(String taskId, int version, String assigneeId) {
        return jdbcClient.sql("""
                        UPDATE operations_task
                        SET task_status = 'IN_PROGRESS', started_at = now(),
                            updated_at = now(), version = version + 1
                        WHERE task_id = :taskId AND version = :version
                          AND task_status = 'CLAIMED' AND assignee_id = :assigneeId
                        """)
                .param("taskId", taskId).param("version", version)
                .param("assigneeId", assigneeId).update();
    }

    /** 输入: 当前版本、领取人和结果; 输出: 完成任务的行数。 */
    public int complete(String taskId, int version, String assigneeId, String resultNote) {
        return jdbcClient.sql("""
                        UPDATE operations_task
                        SET task_status = 'COMPLETED', result_note = :resultNote,
                            completed_at = now(), updated_at = now(), version = version + 1
                        WHERE task_id = :taskId AND version = :version
                          AND task_status = 'IN_PROGRESS' AND assignee_id = :assigneeId
                        """)
                .param("taskId", taskId).param("version", version)
                .param("assigneeId", assigneeId).param("resultNote", resultNote.trim()).update();
    }

    /** 输入: 当前版本和取消原因; 输出: 管理员终止未结束任务的行数。 */
    public int cancel(String taskId, int version, String reason) {
        return jdbcClient.sql("""
                        UPDATE operations_task
                        SET task_status = 'CANCELLED', result_note = :reason,
                            completed_at = now(), updated_at = now(), version = version + 1
                        WHERE task_id = :taskId AND version = :version
                          AND task_status IN ('OPEN', 'CLAIMED', 'IN_PROGRESS')
                        """)
                .param("taskId", taskId).param("version", version).param("reason", reason.trim()).update();
    }

    /** 输入: 车辆编号和生命周期; 输出: 受任务执行影响的车辆行数。 */
    public int updateVehicleLifecycle(String vehicleId, String lifecycleStatus) {
        return jdbcClient.sql("""
                        UPDATE vehicle SET lifecycle_status = :status, updated_at = now()
                        WHERE vehicle_id = :vehicleId AND lifecycle_status NOT IN ('RETIRED', 'IMPOUNDED')
                        """)
                .param("vehicleId", vehicleId).param("status", lifecycleStatus).update();
    }

    /** 输入: 状态迁移及操作者; 输出: 追加不可变任务事件。 */
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
                        """)
                .param("taskId", taskId).param("eventType", eventType.name())
                .param("fromStatus", fromStatus == null ? null : fromStatus.name())
                .param("toStatus", toStatus.name()).param("actorId", actorId)
                .param("actorName", actorName).param("note", blankToNull(note)).update();
    }

    // ==================== SQL 与结果映射 ====================

    /** 输入: 任务筛选; 输出: SQL 条件及其命名参数。 */
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

    /** 输入: SQL 与命名参数; 输出: 已绑定全部参数的语句。 */
    private JdbcClient.StatementSpec bind(JdbcClient.StatementSpec statement, Map<String, Object> parameters) {
        parameters.forEach(statement::param);
        return statement;
    }

    /** 输入: 任务查询行; 输出: 完整任务显示模型。 */
    private TaskItem mapTask(ResultSet rs, int rowNum) throws SQLException {
        return new TaskItem(
                rs.getString("task_id"), rs.getString("task_no"),
                TaskType.valueOf(rs.getString("task_type")), TaskStatus.valueOf(rs.getString("task_status")),
                TaskPriority.valueOf(rs.getString("priority")), rs.getString("title"),
                rs.getString("description"), rs.getString("vehicle_id"), rs.getString("plate_number"),
                rs.getString("city_code"), rs.getString("area_code"), rs.getString("org_id"),
                rs.getString("org_name"), rs.getString("target_name"), rs.getBigDecimal("source_longitude"),
                rs.getBigDecimal("source_latitude"), nullableInteger(rs, "battery_percent"),
                rs.getString("assignee_id"), rs.getString("assignee_name"), rs.getString("created_by"),
                rs.getString("created_by_name"), instant(rs, "due_at"), instant(rs, "claimed_at"),
                instant(rs, "started_at"), instant(rs, "completed_at"), rs.getString("result_note"),
                rs.getInt("version"), instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    /** 输入: 事件查询行; 输出: 状态迁移事件。 */
    private TaskEvent mapEvent(ResultSet rs, int rowNum) throws SQLException {
        var from = rs.getString("from_status");
        return new TaskEvent(rs.getLong("event_id"), TaskEventType.valueOf(rs.getString("event_type")),
                from == null ? null : TaskStatus.valueOf(from), TaskStatus.valueOf(rs.getString("to_status")),
                rs.getString("actor_id"), rs.getString("actor_name"), rs.getString("note"),
                instant(rs, "created_at"));
    }

    /** 输入: 人员查询行; 输出: 可指派人员选项。 */
    private AssigneeOption mapAssignee(ResultSet rs, int rowNum) throws SQLException {
        return new AssigneeOption(rs.getString("user_id"), rs.getString("display_name"),
                rs.getString("phone"), rs.getString("org_id"), rs.getString("org_name"));
    }

    /** 输入: 可空整数字段; 输出: Integer 或 null。 */
    private Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        var value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    /** 输入: 可空时间字段; 输出: Instant 或 null。 */
    private Instant instant(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    /** 输入: 可空字符串; 输出: 去空白后的值或 null。 */
    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record Query(String sql, LinkedHashMap<String, Object> parameters) {
    }
}

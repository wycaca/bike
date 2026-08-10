package cn.bike.platform.ops;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Profile("mock")
@Component
@Order(50)
public class MockOperationsInitializer implements ApplicationRunner {

    private final JdbcClient jdbcClient;

    public MockOperationsInitializer(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * 输入: Spring Boot 启动参数; 输出: 无, 幂等生成包含不同状态的运维任务。
     *
     * 步骤:
     * 1. 在车辆和运维账号初始化后，读取车辆最新位置作为任务位置快照。
     * 2. 写入待抢、已领取、执行中和已完成任务，确保管理端能覆盖完整状态流转。
     * 3. 每个任务至少写入创建事件，已领取及后续状态补齐对应操作轨迹。
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        var now = Instant.now();
        seed(new Seed("OPS-MOCK-BJ-001", "OPS-202608100001", "BATTERY_SWAP", "OPEN", "URGENT",
                "低电量车辆紧急换电", "YD-BJ-000001", "ORG-BJ", null,
                "国贸地铁站 A 口", now.minus(30, ChronoUnit.MINUTES), null));
        seed(new Seed("OPS-MOCK-BJ-002", "OPS-202608100002", "REBALANCE", "OPEN", "HIGH",
                "早高峰车辆调度", "YD-BJ-000002", "ORG-BJ", null,
                "朝阳门地铁站 B 口", now.plus(1, ChronoUnit.HOURS), null));
        seed(new Seed("OPS-MOCK-BJ-003", "OPS-202608100003", "REPAIR", "CLAIMED", "HIGH",
                "智能锁异常检修", "YD-BJ-000003", "ORG-BJ", "USR-OP-BJ",
                "建国门现场", now.plus(2, ChronoUnit.HOURS), null));
        seed(new Seed("OPS-MOCK-BJ-004", "OPS-202608100004", "INSPECTION", "IN_PROGRESS", "NORMAL",
                "重点区域例行巡检", "YD-BJ-000004", "ORG-BJ", "USR-OP-BJ",
                "三里屯运营区", now.plus(3, ChronoUnit.HOURS), null));
        seed(new Seed("OPS-MOCK-BJ-005", "OPS-202608100005", "CLEANING", "COMPLETED", "LOW",
                "车身清洁与贴纸检查", "YD-BJ-000005", "ORG-BJ", "USR-OP-BJ",
                "北京运营仓", now.minus(1, ChronoUnit.HOURS), "车身已清洁，二维码完好"));
        seed(new Seed("OPS-MOCK-SH-001", "OPS-202608100006", "RETRIEVAL", "OPEN", "HIGH",
                "禁停区车辆回收", "YD-SH-000001", "ORG-SH", null,
                "人民广场运营仓", now.plus(90, ChronoUnit.MINUTES), null));
    }

    /** 输入: 单条演示任务; 输出: 无, 任务已存在时不重复生成。 */
    private void seed(Seed seed) {
        var inserted = jdbcClient.sql("""
                        INSERT INTO operations_task (
                            task_id, task_no, task_type, task_status, priority, title,
                            vehicle_id, city_code, area_code, org_id, target_name,
                            source_longitude, source_latitude, battery_percent,
                            assignee_id, created_by, due_at, claimed_at, started_at, completed_at, result_note
                        )
                        SELECT :taskId, :taskNo, :taskType, :taskStatus, :priority, :title,
                               v.vehicle_id, v.operation_city_code, v.operation_area_code, :orgId, :targetName,
                               latest.longitude, latest.latitude, latest.battery_percent,
                               :assigneeId, 'USR-ADMIN', :dueAt,
                               CASE WHEN CAST(:assigneeId AS VARCHAR) IS NULL
                                    THEN NULL ELSE now() - interval '40 minutes' END,
                               CASE WHEN CAST(:taskStatus AS VARCHAR) IN ('IN_PROGRESS', 'COMPLETED')
                                    THEN now() - interval '25 minutes' ELSE NULL END,
                               CASE WHEN CAST(:taskStatus AS VARCHAR) = 'COMPLETED'
                                    THEN now() - interval '5 minutes' ELSE NULL END,
                               :resultNote
                        FROM vehicle v LEFT JOIN vehicle_latest latest ON latest.vehicle_id = v.vehicle_id
                        WHERE v.vehicle_id = :vehicleId
                        ON CONFLICT DO NOTHING
                        """)
                .param("taskId", seed.taskId()).param("taskNo", seed.taskNo())
                .param("taskType", seed.taskType()).param("taskStatus", seed.status())
                .param("priority", seed.priority()).param("title", seed.title())
                .param("vehicleId", seed.vehicleId()).param("orgId", seed.orgId())
                .param("targetName", seed.targetName()).param("assigneeId", seed.assigneeId())
                .param("dueAt", Timestamp.from(seed.dueAt())).param("resultNote", seed.resultNote())
                .update();
        if (inserted == 0) {
            return;
        }

        insertEvent(seed.taskId(), "CREATED", null, "OPEN", "USR-ADMIN", "系统管理员", "创建演示任务");
        if (seed.assigneeId() != null) {
            insertEvent(seed.taskId(), "ASSIGNED", "OPEN", "CLAIMED",
                    "USR-ADMIN", "系统管理员", "指派给演示运维人员");
        }
        if ("IN_PROGRESS".equals(seed.status()) || "COMPLETED".equals(seed.status())) {
            insertEvent(seed.taskId(), "STARTED", "CLAIMED", "IN_PROGRESS",
                    seed.assigneeId(), "北京运维一组", "开始执行任务");
            jdbcClient.sql("UPDATE vehicle SET lifecycle_status = 'MAINTENANCE' WHERE vehicle_id = :vehicleId")
                    .param("vehicleId", seed.vehicleId()).update();
        }
        if ("COMPLETED".equals(seed.status())) {
            insertEvent(seed.taskId(), "COMPLETED", "IN_PROGRESS", "COMPLETED",
                    seed.assigneeId(), "北京运维一组", seed.resultNote());
            jdbcClient.sql("UPDATE vehicle SET lifecycle_status = 'OPERATING' WHERE vehicle_id = :vehicleId")
                    .param("vehicleId", seed.vehicleId()).update();
        }
    }

    /** 输入: 事件状态和说明; 输出: 无, 追加演示任务操作轨迹。 */
    private void insertEvent(
            String taskId,
            String eventType,
            String fromStatus,
            String toStatus,
            String actorId,
            String actorName,
            String note
    ) {
        jdbcClient.sql("""
                        INSERT INTO operations_task_event (
                            task_id, event_type, from_status, to_status, actor_id, actor_name, note
                        ) VALUES (:taskId, :eventType, :fromStatus, :toStatus, :actorId, :actorName, :note)
                        """)
                .param("taskId", taskId).param("eventType", eventType).param("fromStatus", fromStatus)
                .param("toStatus", toStatus).param("actorId", actorId)
                .param("actorName", actorName).param("note", note).update();
    }

    private record Seed(
            String taskId,
            String taskNo,
            String taskType,
            String status,
            String priority,
            String title,
            String vehicleId,
            String orgId,
            String assigneeId,
            String targetName,
            Instant dueAt,
            String resultNote
    ) {
    }
}

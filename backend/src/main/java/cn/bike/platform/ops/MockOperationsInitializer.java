package cn.bike.platform.ops;

import cn.bike.platform.ops.OperationsMapper.MockTask;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Profile("mock")
@Component
@Order(50)
public class MockOperationsInitializer implements ApplicationRunner {

    private final OperationsMapper mapper;

    public MockOperationsInitializer(OperationsMapper mapper) {
        this.mapper = mapper;
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
        var inserted = mapper.insertMockTask(new MockTask(
                seed.taskId(), seed.taskNo(), seed.taskType(), seed.status(), seed.priority(), seed.title(),
                seed.vehicleId(), seed.orgId(), seed.targetName(), seed.assigneeId(), seed.dueAt(),
                seed.resultNote()));
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
            mapper.updateMockVehicleLifecycle(seed.vehicleId(), "MAINTENANCE");
        }
        if ("COMPLETED".equals(seed.status())) {
            insertEvent(seed.taskId(), "COMPLETED", "IN_PROGRESS", "COMPLETED",
                    seed.assigneeId(), "北京运维一组", seed.resultNote());
            mapper.updateMockVehicleLifecycle(seed.vehicleId(), "OPERATING");
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
        mapper.insertMockEvent(taskId, eventType, fromStatus, toStatus, actorId, actorName, note);
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

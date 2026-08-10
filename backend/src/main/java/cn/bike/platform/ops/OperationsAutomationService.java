package cn.bike.platform.ops;

import cn.bike.platform.ops.OperationsModels.AutomationScanResult;
import cn.bike.platform.ops.OperationsModels.AutomationVehicleState;
import cn.bike.platform.ops.OperationsModels.CreateTaskRequest;
import cn.bike.platform.ops.OperationsModels.TaskEventType;
import cn.bike.platform.ops.OperationsModels.TaskRule;
import cn.bike.platform.ops.OperationsModels.TaskSourceType;
import cn.bike.platform.ops.OperationsModels.TaskStatus;
import cn.bike.platform.ops.OperationsModels.TriggerType;
import cn.bike.platform.telemetry.TelemetryModels.YadeaCloudEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class OperationsAutomationService {

    private static final Duration LIVE_EVENT_MAX_AGE = Duration.ofMinutes(30);
    private static final String RULE_ACTOR_NAME = "自动任务规则";

    private final OperationsRepository taskRepository;
    private final OperationsRuleRepository ruleRepository;
    private final JsonMapper jsonMapper;

    public OperationsAutomationService(
            OperationsRepository taskRepository,
            OperationsRuleRepository ruleRepository,
            JsonMapper jsonMapper
    ) {
        this.taskRepository = taskRepository;
        this.ruleRepository = ruleRepository;
        this.jsonMapper = jsonMapper;
    }

    /**
     * 输入: 最新遥测事件; 输出: 无, 对实时事件评估自动任务规则。
     *
     * 只处理30分钟内的事件，避免Kafka历史重放产生过期任务；管理员手工扫描不受该限制。
     */
    @Transactional
    public void processTelemetry(YadeaCloudEvent event) {
        var now = Instant.now();
        if (event.occurredAt().isBefore(now.minus(LIVE_EVENT_MAX_AGE))) {
            return;
        }
        var state = event.state();
        var location = event.location();
        evaluate(new AutomationVehicleState(
                event.vehicleId(), null, null, location.longitude(), location.latitude(),
                state.batteryPercent(), state.online(), state.controllerStatus().name(),
                state.rideStatus().name(), state.faultCodes() == null ? List.of() : state.faultCodes(),
                event.occurredAt()), now);
    }

    /** 输入: 城市; 输出: 对数据库最新车辆状态执行一次规则扫描的统计结果。 */
    @Transactional
    public AutomationScanResult scanCity(String cityCode) {
        if (cityCode == null || !cityCode.matches("\\d{6}")) {
            throw new IllegalArgumentException("cityCode 必须是 6 位行政区划代码");
        }
        var states = taskRepository.findAutomationVehicleStates(cityCode);
        var created = 0;
        var deduplicated = 0;
        var now = Instant.now();
        for (var state : states) {
            var outcome = evaluate(state, now);
            created += outcome.created();
            deduplicated += outcome.deduplicated();
        }
        return new AutomationScanResult(states.size(), created, deduplicated);
    }

    /**
     * 输入: 单车状态和本次评估时间; 输出: 新建与聚合数量。
     *
     * 步骤:
     * 1. 按城市读取启用规则并逐条判断触发条件。
     * 2. 同车已有任务时只追加触发记录和计数，不创建第二张活跃任务。
     * 3. 条件恢复时关闭对应触发；全部触发恢复且规则允许自动关闭时取消待执行任务。
     */
    private EvaluationOutcome evaluate(AutomationVehicleState input, Instant evaluatedAt) {
        var state = enrichState(input);
        if (state.cityCode() == null) {
            return EvaluationOutcome.EMPTY;
        }
        var rules = ruleRepository.findEnabledRules(state.cityCode());
        var rulesById = new HashMap<String, TaskRule>();
        var created = 0;
        var deduplicated = 0;
        for (var rule : rules) {
            rulesById.put(rule.ruleId(), rule);
            var matched = matches(rule, state);
            var triggerKey = triggerKey(rule, state);
            if (matched) {
                var outcome = trigger(rule, state, triggerKey, evaluatedAt);
                created += outcome.created();
                deduplicated += outcome.deduplicated();
            } else {
                taskRepository.recoverTriggers(rule.ruleId(), state.vehicleId(), evaluatedAt);
            }
        }

        taskRepository.findRuleTaskWithoutActiveTriggers(state.vehicleId()).ifPresent(task -> {
            var sourceRule = rulesById.get(task.ruleId());
            if (sourceRule != null && sourceRule.autoClose()
                    && taskRepository.cancel(task.taskId(), task.version(), "监测状态已恢复，规则自动关闭任务") > 0) {
                taskRepository.updateVehicleLifecycle(task.vehicleId(), "OPERATING");
                taskRepository.insertEvent(task.taskId(), TaskEventType.RULE_RECOVERED,
                        task.status(), TaskStatus.CANCELLED, null, RULE_ACTOR_NAME,
                        "全部触发条件已恢复，自动关闭任务");
            }
        });
        return new EvaluationOutcome(created, deduplicated);
    }

    /** 输入: 可能缺少城市字段的实时状态; 输出: 补齐车辆档案字段后的状态。 */
    private AutomationVehicleState enrichState(AutomationVehicleState state) {
        if (state.cityCode() != null) {
            return state;
        }
        return taskRepository.findVehicleSnapshot(state.vehicleId())
                .map(vehicle -> new AutomationVehicleState(
                        state.vehicleId(), vehicle.cityCode(), vehicle.areaCode(), state.longitude(),
                        state.latitude(), state.batteryPercent(), state.online(), state.controllerStatus(),
                        state.rideStatus(), state.faultCodes(), state.occurredAt()))
                .orElse(state);
    }

    /** 输入: 规则和车辆状态; 输出: 创建新任务或聚合到现有任务的数量。 */
    private EvaluationOutcome trigger(
            TaskRule rule,
            AutomationVehicleState state,
            String triggerKey,
            Instant evaluatedAt
    ) {
        var payload = jsonMapper.writeValueAsString(Map.of(
                "vehicleId", state.vehicleId(),
                "batteryPercent", state.batteryPercent() == null ? -1 : state.batteryPercent(),
                "online", state.online(),
                "controllerStatus", state.controllerStatus(),
                "faultCodes", state.faultCodes(),
                "observedAt", state.occurredAt().toString()
        ));
        var activeTask = taskRepository.findActiveTaskForVehicle(state.vehicleId());
        if (activeTask.isPresent()) {
            aggregate(activeTask.get().taskId(), activeTask.get().status(), rule, triggerKey, evaluatedAt, payload);
            return new EvaluationOutcome(0, 1);
        }
        if (taskRepository.hasRecentRuleTask(
                state.vehicleId(), rule.ruleId(), rule.cooldownMinutes(), evaluatedAt)) {
            return EvaluationOutcome.EMPTY;
        }

        var taskId = UUID.randomUUID().toString();
        var taskNo = taskNo();
        var title = render(rule.titleTemplate(), state);
        var description = render(rule.descriptionTemplate(), state);
        var request = new CreateTaskRequest(rule.taskType(), rule.priority(), title, description,
                state.vehicleId(), rule.orgId(), null, evaluatedAt.plus(rule.dueMinutes(), ChronoUnit.MINUTES), null);
        var vehicle = taskRepository.findVehicleSnapshot(state.vehicleId()).orElse(null);
        if (vehicle == null || taskRepository.insertTask(taskId, taskNo, request, vehicle, null, null,
                TaskSourceType.RULE, rule.ruleId(), null, triggerKey) == 0) {
            taskRepository.findActiveTaskForVehicle(state.vehicleId()).ifPresent(task ->
                    aggregate(task.taskId(), task.status(), rule, triggerKey, evaluatedAt, payload));
            return new EvaluationOutcome(0, 1);
        }

        taskRepository.upsertTrigger(taskId, rule.ruleId(), triggerKey, evaluatedAt, payload);
        taskRepository.insertEvent(taskId, TaskEventType.CREATED, null, TaskStatus.OPEN,
                null, RULE_ACTOR_NAME, "规则“" + rule.ruleName() + "”自动生成任务");
        return new EvaluationOutcome(1, 0);
    }

    /** 输入: 已有任务和新触发; 输出: 无, 将重复问题聚合进同一任务。 */
    private void aggregate(
            String taskId,
            TaskStatus taskStatus,
            TaskRule rule,
            String triggerKey,
            Instant evaluatedAt,
            String payload
    ) {
        var occurrence = taskRepository.upsertTrigger(
                taskId, rule.ruleId(), triggerKey, evaluatedAt, payload);
        taskRepository.incrementDuplicateCount(taskId);
        if (occurrence == 1) {
            taskRepository.insertEvent(taskId, TaskEventType.DEDUPLICATED, taskStatus, taskStatus,
                    null, RULE_ACTOR_NAME, "规则“" + rule.ruleName() + "”触发已合并到当前任务");
        }
    }

    private boolean matches(TaskRule rule, AutomationVehicleState state) {
        return switch (rule.triggerType()) {
            case LOW_BATTERY -> state.batteryPercent() != null
                    && state.batteryPercent() <= rule.thresholdValue();
            case VEHICLE_FAULT -> "FAULT".equals(state.controllerStatus()) || !state.faultCodes().isEmpty();
            case VEHICLE_OFFLINE -> !state.online() || "OFFLINE".equals(state.controllerStatus());
            case GEO_VIOLATION -> state.longitude() != null && state.latitude() != null
                    && taskRepository.hasGeoViolation(state);
        };
    }

    private String triggerKey(TaskRule rule, AutomationVehicleState state) {
        if (rule.triggerType() == TriggerType.VEHICLE_FAULT && !state.faultCodes().isEmpty()) {
            return "FAULT:" + state.faultCodes().stream().sorted().reduce((a, b) -> a + "," + b).orElse("UNKNOWN");
        }
        return rule.triggerType().name();
    }

    private String render(String template, AutomationVehicleState state) {
        if (template == null) {
            return null;
        }
        return template.replace("{vehicleId}", state.vehicleId())
                .replace("{batteryPercent}", state.batteryPercent() == null ? "未知" : state.batteryPercent().toString())
                .replace("{faultCodes}", state.faultCodes().isEmpty() ? "无" : String.join(",", state.faultCodes()));
    }

    private String taskNo() {
        return "OPS-" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12).toUpperCase(Locale.ROOT);
    }

    private record EvaluationOutcome(int created, int deduplicated) {
        private static final EvaluationOutcome EMPTY = new EvaluationOutcome(0, 0);
    }
}

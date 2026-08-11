package cn.bike.platform.ops;

import cn.bike.platform.TestDataPermissions;
import cn.bike.platform.ops.OperationsModels.AutomationVehicleState;
import cn.bike.platform.ops.OperationsModels.TaskItem;
import cn.bike.platform.ops.OperationsModels.TaskPriority;
import cn.bike.platform.ops.OperationsModels.TaskRule;
import cn.bike.platform.ops.OperationsModels.TaskSourceType;
import cn.bike.platform.ops.OperationsModels.TaskStatus;
import cn.bike.platform.ops.OperationsModels.TaskType;
import cn.bike.platform.ops.OperationsModels.TriggerType;
import cn.bike.platform.ops.OperationsModels.VehicleSnapshot;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OperationsAutomationServiceTest {

    @Test
    void 低电量首次触发应自动创建任务() {
        var tasks = mock(OperationsRepository.class);
        var rules = mock(OperationsRuleRepository.class);
        var service = new OperationsAutomationService(
                tasks, rules, JsonMapper.builder().build(), TestDataPermissions.allService());
        var state = vehicleState(8);
        when(tasks.findAutomationVehicleStates("110000", TestDataPermissions.ALL)).thenReturn(List.of(state));
        when(rules.findEnabledRules("110000")).thenReturn(List.of(lowBatteryRule(true)));
        when(tasks.findActiveTaskForVehicle(state.vehicleId())).thenReturn(Optional.empty());
        when(tasks.findVehicleSnapshot(state.vehicleId())).thenReturn(Optional.of(vehicle()));
        when(tasks.insertTask(anyString(), anyString(), any(), any(), any(), any(),
                eq(TaskSourceType.RULE), eq("RULE-LOW"), any(), eq("LOW_BATTERY"))).thenReturn(1);
        when(tasks.findRuleTaskWithoutActiveTriggers(state.vehicleId())).thenReturn(Optional.empty());

        var result = service.scanCity("110000");

        assertThat(result.scannedVehicles()).isEqualTo(1);
        assertThat(result.createdTasks()).isEqualTo(1);
        assertThat(result.deduplicatedSignals()).isZero();
        verify(tasks).upsertTrigger(anyString(), eq("RULE-LOW"), eq("LOW_BATTERY"), any(), anyString());
    }

    @Test
    void 同车已有活跃任务时应聚合触发而不是重复建单() {
        var tasks = mock(OperationsRepository.class);
        var rules = mock(OperationsRuleRepository.class);
        var service = new OperationsAutomationService(
                tasks, rules, JsonMapper.builder().build(), TestDataPermissions.allService());
        var state = vehicleState(5);
        when(tasks.findAutomationVehicleStates("110000", TestDataPermissions.ALL)).thenReturn(List.of(state));
        when(rules.findEnabledRules("110000")).thenReturn(List.of(lowBatteryRule(true)));
        when(tasks.findActiveTaskForVehicle(state.vehicleId())).thenReturn(Optional.of(task(TaskStatus.OPEN)));
        when(tasks.upsertTrigger(eq("TASK-1"), eq("RULE-LOW"), eq("LOW_BATTERY"), any(),
                org.mockito.ArgumentMatchers.anyString())).thenReturn(1);
        when(tasks.findRuleTaskWithoutActiveTriggers(state.vehicleId())).thenReturn(Optional.empty());

        var result = service.scanCity("110000");

        assertThat(result.createdTasks()).isZero();
        assertThat(result.deduplicatedSignals()).isEqualTo(1);
        verify(tasks).incrementDuplicateCount("TASK-1");
        verify(tasks, never()).insertTask(anyString(), anyString(), any(), any(), any(), any(),
                any(), any(), any(), any());
    }

    @Test
    void 触发条件恢复后应自动关闭未开工规则任务() {
        var tasks = mock(OperationsRepository.class);
        var rules = mock(OperationsRuleRepository.class);
        var service = new OperationsAutomationService(
                tasks, rules, JsonMapper.builder().build(), TestDataPermissions.allService());
        var state = vehicleState(80);
        var task = task(TaskStatus.OPEN);
        when(tasks.findAutomationVehicleStates("110000", TestDataPermissions.ALL)).thenReturn(List.of(state));
        when(rules.findEnabledRules("110000")).thenReturn(List.of(lowBatteryRule(true)));
        when(tasks.findRuleTaskWithoutActiveTriggers(state.vehicleId())).thenReturn(Optional.of(task));
        when(tasks.cancel(task.taskId(), task.version(), "监测状态已恢复，规则自动关闭任务")).thenReturn(1);

        service.scanCity("110000");

        verify(tasks).recoverTriggers(eq("RULE-LOW"), eq(state.vehicleId()), any());
        verify(tasks).updateVehicleLifecycle(state.vehicleId(), "OPERATING");
    }

    private TaskRule lowBatteryRule(boolean autoClose) {
        var now = Instant.parse("2026-08-11T01:00:00Z");
        return new TaskRule("RULE-LOW", "低电量自动换电", "110000", "ORG-BJ", "北京运营中心",
                TriggerType.LOW_BATTERY, 15, TaskType.BATTERY_SWAP, TaskPriority.URGENT,
                "车辆{vehicleId}低电量", "当前电量{batteryPercent}%", 60, 30,
                autoClose, true, 0, now, now);
    }

    private AutomationVehicleState vehicleState(int battery) {
        return new AutomationVehicleState("YD-BJ-000001", "ORG-BJ", "110000", "110105",
                new BigDecimal("116.400000"), new BigDecimal("39.900000"), battery,
                true, "NORMAL", "IDLE", List.of(), Instant.parse("2026-08-11T01:00:00Z"));
    }

    private VehicleSnapshot vehicle() {
        return new VehicleSnapshot("YD-BJ-000001", "ORG-BJ", "110000", "110105",
                new BigDecimal("116.400000"), new BigDecimal("39.900000"), 8);
    }

    private TaskItem task(TaskStatus status) {
        var now = Instant.parse("2026-08-11T01:00:00Z");
        return new TaskItem("TASK-1", "OPS-1", TaskType.BATTERY_SWAP, status, TaskPriority.URGENT,
                TaskSourceType.RULE, "低电量换电", null, "YD-BJ-000001", "京A00001",
                "110000", "110105", "ORG-BJ", "北京运营中心", null,
                new BigDecimal("116.4"), new BigDecimal("39.9"), 8,
                null, null, null, "自动任务规则", "RULE-LOW", "低电量自动换电",
                null, null, "LOW_BATTERY", 0, now.plusSeconds(3600), null, null,
                null, null, null, null, null, null, 0, now, now);
    }
}

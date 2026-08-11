package cn.bike.platform.telemetry;

import cn.bike.platform.ops.OperationsAutomationService;
import cn.bike.platform.telemetry.TelemetryModels.LocationData;
import cn.bike.platform.telemetry.TelemetryModels.StateData;
import cn.bike.platform.telemetry.TelemetryModels.YadeaCloudEvent;
import cn.bike.platform.vehicle.LatestVehicleCache;
import cn.bike.platform.vehicle.VehicleModels.ControllerStatus;
import cn.bike.platform.vehicle.VehicleModels.LockStatus;
import cn.bike.platform.vehicle.VehicleModels.RideStatus;
import cn.bike.platform.vehicle.VehicleRepository;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelemetryProcessorTest {

    @Test
    void Redis短暂故障时重放应补写缓存且不漏掉自动任务() {
        var repository = mock(VehicleRepository.class);
        var cache = mock(LatestVehicleCache.class);
        var jsonMapper = mock(JsonMapper.class);
        var automation = mock(OperationsAutomationService.class);
        var processor = new TelemetryProcessor(repository, cache, jsonMapper, automation);
        var event = event();
        when(jsonMapper.writeValueAsString(any())).thenReturn("{}");
        when(repository.saveTelemetry(any(), anyString(), anyString())).thenReturn(true);
        doThrow(new IllegalStateException("Redis 暂不可用")).doNothing()
                .when(cache).put(anyString(), any());

        assertThatThrownBy(() -> processor.process(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Redis");
        processor.process(event);

        var order = inOrder(automation, cache);
        order.verify(automation).processTelemetry(event);
        order.verify(cache).put(anyString(), any());
        verify(automation, times(2)).processTelemetry(event);
        verify(cache, times(2)).put(anyString(), any());
    }

    private YadeaCloudEvent event() {
        return new YadeaCloudEvent("EVT-1", "BIKE-001", "CTRL-001",
                Instant.parse("2026-08-10T01:02:03Z"),
                new LocationData(new BigDecimal("116.397"), new BigDecimal("39.908"), "WGS84",
                        new BigDecimal("5"), BigDecimal.ZERO, 0, 8),
                new StateData(LockStatus.LOCKED, RideStatus.IDLE, ControllerStatus.NORMAL,
                        18, new BigDecimal("12.5"), true, 4, List.of()),
                Map.of());
    }
}

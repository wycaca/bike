package cn.bike.platform.telemetry;

import cn.bike.platform.ops.OperationsAutomationService;
import cn.bike.platform.telemetry.TelemetryModels.YadeaCloudEvent;
import cn.bike.platform.vehicle.LatestVehicleCache;
import cn.bike.platform.vehicle.VehicleModels.LatestState;
import cn.bike.platform.vehicle.VehicleRepository;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

@Service
public class TelemetryProcessor {

    private final VehicleRepository vehicleRepository;
    private final LatestVehicleCache latestVehicleCache;
    private final JsonMapper jsonMapper;
    private final OperationsAutomationService operationsAutomationService;

    public TelemetryProcessor(
            VehicleRepository vehicleRepository,
            LatestVehicleCache latestVehicleCache,
            JsonMapper jsonMapper,
            OperationsAutomationService operationsAutomationService
    ) {
        this.vehicleRepository = vehicleRepository;
        this.latestVehicleCache = latestVehicleCache;
        this.jsonMapper = jsonMapper;
        this.operationsAutomationService = operationsAutomationService;
    }

    /**
     * 将雅迪云临时模型转换为平台标准状态. 数据库先幂等落盘，再生成去重任务，最后刷新 Redis。
     * 相同时间戳允许 Kafka 重放，因此 Redis 短暂故障恢复后可补写缓存，轨迹唯一键不会产生重复点。
     */
    public void process(YadeaCloudEvent event) {
        var faultCodes = event.state().faultCodes() == null ? List.<String>of() : event.state().faultCodes();
        var rawPayload = jsonMapper.writeValueAsString(event);
        var latestUpdated = vehicleRepository.saveTelemetry(
                event, rawPayload, jsonMapper.writeValueAsString(faultCodes));
        if (!latestUpdated) {
            return;
        }

        var location = event.location();
        var state = event.state();
        operationsAutomationService.processTelemetry(event);
        latestVehicleCache.put(event.vehicleId(), new LatestState(
                event.occurredAt(),
                location.longitude(),
                location.latitude(),
                location.accuracyMeters(),
                location.speedKmh(),
                location.directionDegrees(),
                location.satelliteCount(),
                state.batteryPercent(),
                state.remainingRangeKm(),
                state.lockStatus(),
                state.rideStatus(),
                state.controllerStatus(),
                state.online(),
                state.signalStrength(),
                faultCodes,
                location.coordinateSystem()));
    }
}

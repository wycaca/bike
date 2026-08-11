package cn.bike.platform.vehicle;

import cn.bike.platform.telemetry.TelemetryModels.YadeaCloudEvent;
import cn.bike.platform.security.DataPermission;
import cn.bike.platform.vehicle.VehicleMapper.ClusterRow;
import cn.bike.platform.vehicle.VehicleMapper.TelemetryWrite;
import cn.bike.platform.vehicle.VehicleMapper.TrajectoryRow;
import cn.bike.platform.vehicle.VehicleMapper.VehicleRow;
import cn.bike.platform.vehicle.VehicleModels.LatestState;
import cn.bike.platform.vehicle.VehicleModels.LifecycleStatus;
import cn.bike.platform.vehicle.VehicleModels.MapMarker;
import cn.bike.platform.vehicle.VehicleModels.PageData;
import cn.bike.platform.vehicle.VehicleModels.TrajectoryPoint;
import cn.bike.platform.vehicle.VehicleModels.VehicleAsset;
import cn.bike.platform.vehicle.VehicleModels.VehicleDetail;
import cn.bike.platform.vehicle.VehicleModels.VehicleListItem;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 车辆域仓储, 负责查询参数、数据库行模型和车辆领域对象之间的转换.
 * 遥测历史与最新状态由 Mapper 分别写入, 最新状态影响行数用于判断是否刷新缓存.
 */
@Repository
public class VehicleRepository {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final VehicleMapper mapper;
    private final JsonMapper jsonMapper;

    public VehicleRepository(VehicleMapper mapper, JsonMapper jsonMapper) {
        this.mapper = mapper;
        this.jsonMapper = jsonMapper;
    }

    /** 输入: 模拟车辆档案; 输出: 无, 按车辆编号幂等更新。 */
    public void upsertVehicle(VehicleAsset asset) {
        mapper.upsertVehicle(asset);
    }

    /**
     * 输入: 遥测事件和原始 JSON; 输出: 最新状态是否被本次事件更新.
     *
     * 轨迹幂等和乱序保护保留在 PostgreSQL SQL 中, 事务仍由 Spring 管理.
     */
    @Transactional
    public boolean saveTelemetry(YadeaCloudEvent event, String rawPayload, String faultCodesJson) {
        var location = event.location();
        var state = event.state();
        var row = new TelemetryWrite(
                event.vehicleId(), event.occurredAt(), location.longitude(), location.latitude(),
                location.accuracyMeters(), location.speedKmh(), location.directionDegrees(),
                location.satelliteCount(), state.batteryPercent(), state.remainingRangeKm(),
                state.lockStatus().name(), state.rideStatus().name(), state.controllerStatus().name(),
                state.online(), state.signalStrength(), faultCodesJson, rawPayload);
        // 历史点始终尝试幂等写入; 只有较新的快照会返回成功并触发上层 Redis 更新.
        mapper.insertVehiclePosition(row);
        return mapper.upsertVehicleLatest(row) > 0;
    }

    public PageData<VehicleListItem> findVehicles(
            int page,
            int pageSize,
            String keyword,
            String cityCode,
            LifecycleStatus lifecycleStatus,
            DataPermission permission
    ) {
        var normalizedKeyword = fuzzyOrNull(keyword);
        var normalizedCityCode = trimmedOrNull(cityCode);
        var normalizedStatus = lifecycleStatus == null ? null : lifecycleStatus.name();
        var total = mapper.countVehicles(normalizedKeyword, normalizedCityCode, normalizedStatus, permission);
        var items = mapper.findVehicles(normalizedKeyword, normalizedCityCode, normalizedStatus,
                        permission, pageSize, (page - 1) * pageSize)
                .stream().map(this::mapVehicleListItem).toList();
        return new PageData<>(items, total, page, pageSize);
    }

    public Optional<VehicleDetail> findVehicle(String vehicleId, DataPermission permission) {
        return Optional.ofNullable(mapper.findVehicle(vehicleId, permission))
                .map(row -> new VehicleDetail(mapVehicleAsset(row), mapLatestState(row)));
    }

    public List<TrajectoryPoint> findTrajectory(
            String vehicleId,
            java.time.Instant startTime,
            java.time.Instant endTime,
            int limit
    ) {
        return mapper.findTrajectory(vehicleId, startTime, endTime, limit).stream()
                .map(this::mapTrajectoryPoint).toList();
    }

    public List<MapMarker> findMapVehicles(
            BigDecimal minLongitude,
            BigDecimal minLatitude,
            BigDecimal maxLongitude,
            BigDecimal maxLatitude,
            Boolean online,
            LifecycleStatus lifecycleStatus,
            int limit,
            DataPermission permission
    ) {
        var status = lifecycleStatus == null ? null : lifecycleStatus.name();
        return mapper.findMapVehicles(minLongitude, minLatitude, maxLongitude, maxLatitude, online, status,
                        permission, limit)
                .stream().map(this::mapVehicleMarker).toList();
    }

    public List<MapMarker> findMapClusters(
            BigDecimal minLongitude,
            BigDecimal minLatitude,
            BigDecimal maxLongitude,
            BigDecimal maxLatitude,
            Boolean online,
            LifecycleStatus lifecycleStatus,
            BigDecimal gridSize,
            int limit,
            DataPermission permission
    ) {
        var status = lifecycleStatus == null ? null : lifecycleStatus.name();
        var rows = mapper.findMapClusters(
                minLongitude, minLatitude, maxLongitude, maxLatitude, online, status, permission, gridSize, limit);
        var markers = new ArrayList<MapMarker>(rows.size());
        for (var index = 0; index < rows.size(); index++) {
            markers.add(mapClusterMarker(rows.get(index), index));
        }
        return markers;
    }

    private VehicleListItem mapVehicleListItem(VehicleRow row) {
        return new VehicleListItem(row.vehicleId(), row.plateNumber(), row.filingCode(), row.model(),
                row.operationCityCode(), row.operationAreaCode(), row.lifecycleStatus(), mapLatestState(row));
    }

    private VehicleAsset mapVehicleAsset(VehicleRow row) {
        return new VehicleAsset(
                row.vehicleId(), row.companyId(), row.orgId(), row.lockId(), row.controllerId(), row.plateNumber(),
                row.filingCode(), row.model(), row.batchNo(), row.operationCityCode(),
                row.operationAreaCode(), row.launchDate(), row.lifecycleStatus());
    }

    private LatestState mapLatestState(VehicleRow row) {
        if (row.reportedAt() == null) {
            return null;
        }
        return new LatestState(
                row.reportedAt(), row.longitude(), row.latitude(), row.accuracyMeters(), row.speedKmh(),
                row.directionDegrees(), row.satelliteCount(), row.batteryPercent(), row.remainingRangeKm(),
                row.lockStatus(), row.rideStatus(), row.controllerStatus(), Boolean.TRUE.equals(row.online()),
                row.signalStrength(), parseFaultCodes(row.faultCodes()), "WGS84");
    }

    private TrajectoryPoint mapTrajectoryPoint(TrajectoryRow row) {
        return new TrajectoryPoint(
                row.reportedAt(), row.longitude(), row.latitude(), row.accuracyMeters(), row.speedKmh(),
                row.directionDegrees(), row.batteryPercent(), row.lockStatus(), row.rideStatus(), "WGS84");
    }

    private MapMarker mapVehicleMarker(VehicleRow row) {
        var latest = mapLatestState(row);
        return new MapMarker(
                "VEHICLE", row.vehicleId(), row.vehicleId(), latest.longitude(), latest.latitude(), 1,
                latest.batteryPercent() != null && latest.batteryPercent() < 20 ? 1 : 0,
                latest.faultCodes().isEmpty() ? 0 : 1, latest.batteryPercent(), row.lifecycleStatus(), latest);
    }

    private MapMarker mapClusterMarker(ClusterRow row, int index) {
        return new MapMarker(
                "CLUSTER", "cluster-" + index, null, row.longitude(), row.latitude(), row.vehicleCount(),
                row.lowBatteryCount(), row.faultCount(), null, null, null);
    }

    private List<String> parseFaultCodes(String json) {
        return json == null ? List.of() : jsonMapper.readValue(json, STRING_LIST_TYPE);
    }

    private String fuzzyOrNull(String value) {
        return value == null || value.isBlank() ? null : "%" + value.trim() + "%";
    }

    private String trimmedOrNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

package cn.bike.platform.vehicle;

import cn.bike.platform.common.NotFoundException;
import cn.bike.platform.security.DataPermissionService;
import cn.bike.platform.security.PlatformPrincipal;
import cn.bike.platform.vehicle.VehicleModels.CoordinateSystem;
import cn.bike.platform.vehicle.VehicleModels.LatestState;
import cn.bike.platform.vehicle.VehicleModels.LifecycleStatus;
import cn.bike.platform.vehicle.VehicleModels.MapMarker;
import cn.bike.platform.vehicle.VehicleModels.MapResult;
import cn.bike.platform.vehicle.VehicleModels.PageData;
import cn.bike.platform.vehicle.VehicleModels.TrajectoryPoint;
import cn.bike.platform.vehicle.VehicleModels.VehicleDetail;
import cn.bike.platform.vehicle.VehicleModels.VehicleListItem;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class VehicleService {

    private static final int MAP_LIMIT = 5000;
    private static final int TRAJECTORY_LIMIT = 10001;
    private static final int CLUSTER_ZOOM_THRESHOLD = 15;

    private final VehicleRepository repository;
    private final LatestVehicleCache latestVehicleCache;
    private final DataPermissionService dataPermissionService;

    public VehicleService(
            VehicleRepository repository,
            LatestVehicleCache latestVehicleCache,
            DataPermissionService dataPermissionService
    ) {
        this.repository = repository;
        this.latestVehicleCache = latestVehicleCache;
        this.dataPermissionService = dataPermissionService;
    }

    public PageData<VehicleListItem> findVehicles(
            int page,
            int pageSize,
            String keyword,
            String cityCode,
            LifecycleStatus lifecycleStatus,
            PlatformPrincipal principal
    ) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("page 必须大于 0, pageSize 必须在 1 到 100 之间");
        }
        return repository.findVehicles(page, pageSize, keyword, cityCode, lifecycleStatus,
                dataPermissionService.resolve(principal));
    }

    /**
     * 查询车辆详情时优先读取 Redis 最新状态, 缓存缺失时回退到 PostgreSQL 最新投影.
     */
    public VehicleDetail findVehicle(String vehicleId, PlatformPrincipal principal) {
        var detail = repository.findVehicle(vehicleId, dataPermissionService.resolve(principal))
                .orElseThrow(() -> new NotFoundException("车辆不存在: " + vehicleId));
        var latestState = latestVehicleCache.get(vehicleId).orElse(detail.latestState());
        return new VehicleDetail(detail.asset(), latestState);
    }

    /**
     * 查询轨迹时限制最大时间跨度和返回点数, 并按客户端目标坐标系转换接口副本.
     */
    public TrajectoryResult findTrajectory(
            String vehicleId,
            Instant startTime,
            Instant endTime,
            CoordinateSystem coordinateSystem,
            PlatformPrincipal principal
    ) {
        if (!startTime.isBefore(endTime)) {
            throw new IllegalArgumentException("startTime 必须早于 endTime");
        }
        if (Duration.between(startTime, endTime).toDays() > 31) {
            throw new IllegalArgumentException("单次轨迹查询不能超过 31 天");
        }
        repository.findVehicle(vehicleId, dataPermissionService.resolve(principal))
                .orElseThrow(() -> new NotFoundException("车辆不存在: " + vehicleId));
        var points = repository.findTrajectory(vehicleId, startTime, endTime, TRAJECTORY_LIMIT);
        var truncated = points.size() == TRAJECTORY_LIMIT;
        if (truncated) {
            points = points.subList(0, TRAJECTORY_LIMIT - 1);
        }
        return new TrajectoryResult(
                convertTrajectory(points, coordinateSystem), truncated, coordinateSystem.name());
    }

    /**
     * 地图请求边界和返回点使用同一坐标系. 数据库查询前反算为 WGS84, 原始坐标不会被覆盖.
     */
    public MapResult findMap(
            BigDecimal minLongitude,
            BigDecimal minLatitude,
            BigDecimal maxLongitude,
            BigDecimal maxLatitude,
            int zoom,
            Boolean online,
            LifecycleStatus lifecycleStatus,
            CoordinateSystem coordinateSystem,
            PlatformPrincipal principal
    ) {
        validateBounds(minLongitude, minLatitude, maxLongitude, maxLatitude, zoom);
        var queryMin = CoordinateConverter.convert(
                minLongitude, minLatitude, coordinateSystem, CoordinateSystem.WGS84);
        var queryMax = CoordinateConverter.convert(
                maxLongitude, maxLatitude, coordinateSystem, CoordinateSystem.WGS84);
        var clustered = zoom < CLUSTER_ZOOM_THRESHOLD;
        var permission = dataPermissionService.resolve(principal);
        var markers = clustered
                ? repository.findMapClusters(
                        queryMin.longitude(), queryMin.latitude(), queryMax.longitude(), queryMax.latitude(),
                        online, lifecycleStatus, gridSizeForZoom(zoom), MAP_LIMIT, permission)
                : repository.findMapVehicles(
                        queryMin.longitude(), queryMin.latitude(), queryMax.longitude(), queryMax.latitude(),
                        online, lifecycleStatus, MAP_LIMIT, permission);
        return new MapResult(
                convertMarkers(markers, coordinateSystem), clustered, coordinateSystem.name());
    }

    static BigDecimal gridSizeForZoom(int zoom) {
        var degrees = 360.0 / Math.pow(2, zoom) / 4.0;
        return BigDecimal.valueOf(degrees).setScale(8, RoundingMode.HALF_UP);
    }

    private List<TrajectoryPoint> convertTrajectory(
            List<TrajectoryPoint> points,
            CoordinateSystem coordinateSystem
    ) {
        if (coordinateSystem == CoordinateSystem.WGS84) {
            return points;
        }
        return points.stream().map(point -> {
            var coordinate = CoordinateConverter.convert(
                    point.longitude(), point.latitude(), CoordinateSystem.WGS84, coordinateSystem);
            return new TrajectoryPoint(
                    point.reportedAt(), coordinate.longitude(), coordinate.latitude(),
                    point.accuracyMeters(), point.speedKmh(), point.directionDegrees(),
                    point.batteryPercent(), point.lockStatus(), point.rideStatus(), coordinateSystem.name());
        }).toList();
    }

    private List<MapMarker> convertMarkers(
            List<MapMarker> markers,
            CoordinateSystem coordinateSystem
    ) {
        if (coordinateSystem == CoordinateSystem.WGS84) {
            return markers;
        }
        return markers.stream().map(marker -> {
            var coordinate = CoordinateConverter.convert(
                    marker.longitude(), marker.latitude(), CoordinateSystem.WGS84, coordinateSystem);
            return new MapMarker(
                    marker.markerType(), marker.markerId(), marker.vehicleId(),
                    coordinate.longitude(), coordinate.latitude(), marker.vehicleCount(),
                    marker.lowBatteryCount(), marker.faultCount(), marker.batteryPercent(),
                    marker.lifecycleStatus(), convertLatestState(marker.latestState(), coordinateSystem));
        }).toList();
    }

    private LatestState convertLatestState(
            LatestState state,
            CoordinateSystem coordinateSystem
    ) {
        if (state == null) {
            return null;
        }
        var coordinate = CoordinateConverter.convert(
                state.longitude(), state.latitude(), CoordinateSystem.WGS84, coordinateSystem);
        return new LatestState(
                state.reportedAt(), coordinate.longitude(), coordinate.latitude(),
                state.accuracyMeters(), state.speedKmh(), state.directionDegrees(),
                state.satelliteCount(), state.batteryPercent(), state.remainingRangeKm(),
                state.lockStatus(), state.rideStatus(), state.controllerStatus(), state.online(),
                state.signalStrength(), state.faultCodes(), coordinateSystem.name());
    }

    private void validateBounds(
            BigDecimal minLongitude,
            BigDecimal minLatitude,
            BigDecimal maxLongitude,
            BigDecimal maxLatitude,
            int zoom
    ) {
        if (zoom < 3 || zoom > 20) {
            throw new IllegalArgumentException("zoom 必须在 3 到 20 之间");
        }
        if (minLongitude.compareTo(maxLongitude) >= 0 || minLatitude.compareTo(maxLatitude) >= 0) {
            throw new IllegalArgumentException("地图边界参数不合法");
        }
        if (minLongitude.compareTo(BigDecimal.valueOf(-180)) < 0
                || maxLongitude.compareTo(BigDecimal.valueOf(180)) > 0
                || minLatitude.compareTo(BigDecimal.valueOf(-90)) < 0
                || maxLatitude.compareTo(BigDecimal.valueOf(90)) > 0) {
            throw new IllegalArgumentException("经纬度超出合法范围");
        }
    }
}

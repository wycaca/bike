package cn.bike.platform.vehicle;

import cn.bike.platform.vehicle.VehicleModels.ControllerStatus;
import cn.bike.platform.vehicle.VehicleModels.LifecycleStatus;
import cn.bike.platform.vehicle.VehicleModels.LockStatus;
import cn.bike.platform.vehicle.VehicleModels.RideStatus;
import cn.bike.platform.vehicle.VehicleModels.VehicleAsset;
import cn.bike.platform.security.DataPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 车辆持久化 Mapper, 覆盖资产查询、最新状态、历史轨迹和地图聚合.
 * PostGIS、TimescaleDB、轨迹幂等和乱序保护保留在 XML SQL 中, 避免 Java 层竞态.
 */
@Mapper
public interface VehicleMapper {

    int upsertVehicle(VehicleAsset asset);

    int insertVehicle(VehicleAsset asset);

    int insertVehiclePosition(TelemetryWrite row);

    int upsertVehicleLatest(TelemetryWrite row);

    long countVehicles(
            @Param("keyword") String keyword,
            @Param("cityCode") String cityCode,
            @Param("lifecycleStatus") String lifecycleStatus,
            @Param("permission") DataPermission permission
    );

    List<VehicleRow> findVehicles(
            @Param("keyword") String keyword,
            @Param("cityCode") String cityCode,
            @Param("lifecycleStatus") String lifecycleStatus,
            @Param("permission") DataPermission permission,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    VehicleRow findVehicle(
            @Param("vehicleId") String vehicleId,
            @Param("permission") DataPermission permission
    );

    List<TrajectoryRow> findTrajectory(
            @Param("vehicleId") String vehicleId,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime,
            @Param("limit") int limit
    );

    List<VehicleRow> findMapVehicles(
            @Param("minLongitude") BigDecimal minLongitude,
            @Param("minLatitude") BigDecimal minLatitude,
            @Param("maxLongitude") BigDecimal maxLongitude,
            @Param("maxLatitude") BigDecimal maxLatitude,
            @Param("online") Boolean online,
            @Param("lifecycleStatus") String lifecycleStatus,
            @Param("permission") DataPermission permission,
            @Param("limit") int limit
    );

    List<ClusterRow> findMapClusters(
            @Param("minLongitude") BigDecimal minLongitude,
            @Param("minLatitude") BigDecimal minLatitude,
            @Param("maxLongitude") BigDecimal maxLongitude,
            @Param("maxLatitude") BigDecimal maxLatitude,
            @Param("online") Boolean online,
            @Param("lifecycleStatus") String lifecycleStatus,
            @Param("permission") DataPermission permission,
            @Param("gridSize") BigDecimal gridSize,
            @Param("limit") int limit
    );

    record TelemetryWrite(
            String vehicleId,
            Instant reportedAt,
            BigDecimal longitude,
            BigDecimal latitude,
            BigDecimal accuracyMeters,
            BigDecimal speedKmh,
            Integer directionDegrees,
            Integer satelliteCount,
            Integer batteryPercent,
            BigDecimal remainingRangeKm,
            String lockStatus,
            String rideStatus,
            String controllerStatus,
            boolean online,
            Integer signalStrength,
            String faultCodesJson,
            String rawPayload
    ) {
    }

    record VehicleRow(
            String vehicleId,
            String companyId,
            String orgId,
            String lockId,
            String controllerId,
            String plateNumber,
            String filingCode,
            String model,
            String batchNo,
            String operationCityCode,
            String operationAreaCode,
            LocalDate launchDate,
            LifecycleStatus lifecycleStatus,
            Instant reportedAt,
            BigDecimal longitude,
            BigDecimal latitude,
            BigDecimal accuracyMeters,
            BigDecimal speedKmh,
            Integer directionDegrees,
            Integer satelliteCount,
            Integer batteryPercent,
            BigDecimal remainingRangeKm,
            LockStatus lockStatus,
            RideStatus rideStatus,
            ControllerStatus controllerStatus,
            Boolean online,
            Integer signalStrength,
            String faultCodes
    ) {
    }

    record TrajectoryRow(
            Instant reportedAt,
            BigDecimal longitude,
            BigDecimal latitude,
            BigDecimal accuracyMeters,
            BigDecimal speedKmh,
            Integer directionDegrees,
            Integer batteryPercent,
            LockStatus lockStatus,
            RideStatus rideStatus
    ) {
    }

    record ClusterRow(
            BigDecimal longitude,
            BigDecimal latitude,
            long vehicleCount,
            long lowBatteryCount,
            long faultCount
    ) {
    }
}

package cn.bike.platform.vehicle;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class VehicleModels {

    private VehicleModels() {
    }

    public enum CoordinateSystem {
        WGS84,
        GCJ02
    }

    public enum LifecycleStatus {
        PENDING,
        OPERATING,
        MAINTENANCE,
        DISPATCHING,
        RETIRED,
        IMPOUNDED
    }

    public enum LockStatus {
        LOCKED,
        UNLOCKED,
        UNKNOWN
    }

    public enum RideStatus {
        IDLE,
        RIDING,
        DISPATCHING,
        MAINTENANCE
    }

    public enum ControllerStatus {
        NORMAL,
        FAULT,
        OFFLINE
    }

    public record VehicleAsset(
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
            LifecycleStatus lifecycleStatus
    ) {
    }

    public record LatestState(
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
            boolean online,
            Integer signalStrength,
            List<String> faultCodes,
            String coordinateSystem
    ) {
    }

    public record VehicleListItem(
            String vehicleId,
            String plateNumber,
            String filingCode,
            String model,
            String operationCityCode,
            String operationAreaCode,
            LifecycleStatus lifecycleStatus,
            LatestState latestState
    ) {
    }

    public record VehicleDetail(
            VehicleAsset asset,
            LatestState latestState
    ) {
    }

    public record TrajectoryPoint(
            Instant reportedAt,
            BigDecimal longitude,
            BigDecimal latitude,
            BigDecimal accuracyMeters,
            BigDecimal speedKmh,
            Integer directionDegrees,
            Integer batteryPercent,
            LockStatus lockStatus,
            RideStatus rideStatus,
            String coordinateSystem
    ) {
    }

    public record PageData<T>(
            List<T> items,
            long total,
            int page,
            int pageSize
    ) {
    }

    public record MapMarker(
            String markerType,
            String markerId,
            String vehicleId,
            BigDecimal longitude,
            BigDecimal latitude,
            long vehicleCount,
            long lowBatteryCount,
            long faultCount,
            Integer batteryPercent,
            LifecycleStatus lifecycleStatus,
            LatestState latestState
    ) {
    }

    public record MapResult(
            List<MapMarker> markers,
            boolean clustered,
            String coordinateSystem
    ) {
    }
}

package cn.bike.platform.telemetry;

import cn.bike.platform.vehicle.VehicleModels.ControllerStatus;
import cn.bike.platform.vehicle.VehicleModels.LockStatus;
import cn.bike.platform.vehicle.VehicleModels.RideStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class TelemetryModels {

    private TelemetryModels() {
    }

    /**
     * 雅迪公开资料未提供企业 API 字段, 此结构仅表示平台内部的临时标准模型.
     * 获取正式协议后, 只替换雅迪字段映射, 不改变下游车辆和轨迹模型.
     */
    public record YadeaCloudEvent(
            @NotBlank String eventId,
            @NotBlank String vehicleId,
            @NotBlank String deviceId,
            @NotNull Instant occurredAt,
            @NotNull @Valid LocationData location,
            @NotNull @Valid StateData state,
            Map<String, Object> rawData
    ) {
    }

    public record LocationData(
            @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
            @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
            @NotNull @Pattern(regexp = "WGS84", message = "当前仅接收 WGS84") String coordinateSystem,
            @DecimalMin("0") BigDecimal accuracyMeters,
            @DecimalMin("0") BigDecimal speedKmh,
            @Min(0) @Max(359) Integer directionDegrees,
            @Min(0) Integer satelliteCount
    ) {
    }

    public record StateData(
            @NotNull LockStatus lockStatus,
            @NotNull RideStatus rideStatus,
            @NotNull ControllerStatus controllerStatus,
            @NotNull @Min(0) @Max(100) Integer batteryPercent,
            @DecimalMin("0") BigDecimal remainingRangeKm,
            boolean online,
            Integer signalStrength,
            List<String> faultCodes
    ) {
    }
}

package cn.bike.platform.geo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class GeoModels {

    private GeoModels() {
    }

    public enum FenceType { OPERATION, NO_RIDE, NO_PARK }

    public enum FacilityStatus { ACTIVE, DISABLED }

    public record Coordinate(
            @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
            @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude
    ) {
    }

    public record GeofenceRequest(
            @NotBlank @Size(max = 64) String fenceName,
            @NotBlank @Pattern(regexp = "^[0-9]{6}$") String cityCode,
            @NotNull FenceType fenceType,
            @NotBlank String orgId,
            @NotNull FacilityStatus status,
            @NotEmpty @Size(min = 4, max = 200) List<@Valid Coordinate> boundary
    ) {
    }

    public record Geofence(
            String fenceId,
            String fenceName,
            String cityCode,
            FenceType fenceType,
            String orgId,
            String orgName,
            FacilityStatus status,
            List<Coordinate> boundary,
            BigDecimal areaSquareMeters,
            Instant updatedAt
    ) {
    }

    public record ParkingPointRequest(
            @NotBlank @Size(max = 64) String pointName,
            @NotBlank @Pattern(regexp = "^[0-9]{6}$") String cityCode,
            @NotBlank String orgId,
            @NotNull FacilityStatus status,
            @NotNull @Valid Coordinate location,
            @NotNull @DecimalMin("10") @DecimalMax("2000") BigDecimal radiusMeters,
            @NotNull @Min(1) @Max(10000) Integer capacity
    ) {
    }

    public record ParkingPoint(
            String pointId,
            String pointName,
            String cityCode,
            String orgId,
            String orgName,
            FacilityStatus status,
            Coordinate location,
            BigDecimal radiusMeters,
            int capacity,
            long vehicleCount,
            Instant updatedAt
    ) {
    }

    public record GeoViolation(
            String vehicleId,
            String violationType,
            String facilityId,
            String facilityName,
            BigDecimal longitude,
            BigDecimal latitude,
            Integer batteryPercent,
            Instant reportedAt
    ) {
    }

    public record GeoOverview(
            List<Geofence> fences,
            List<ParkingPoint> parkingPoints,
            List<GeoViolation> violations
    ) {
    }
}

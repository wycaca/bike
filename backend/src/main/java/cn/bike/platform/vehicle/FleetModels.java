package cn.bike.platform.vehicle;

import cn.bike.platform.vehicle.VehicleModels.LifecycleStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public final class FleetModels {

    private FleetModels() {
    }

    public record VehicleCreateRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{3,32}$") String vehicleId,
            @NotBlank @Size(max = 16) String companyId,
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{3,32}$") String lockId,
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{3,32}$") String controllerId,
            @Size(max = 32) String plateNumber,
            @Size(max = 32) String filingCode,
            @NotBlank @Size(max = 64) String model,
            @Size(max = 32) String batchNo,
            @NotBlank @Pattern(regexp = "^[0-9]{6}$") String operationCityCode,
            @NotBlank @Pattern(regexp = "^[0-9]{6}$") String operationAreaCode,
            @NotNull LocalDate launchDate,
            @NotNull LifecycleStatus lifecycleStatus
    ) {
    }

    public record VehicleBatchRequest(
            @NotEmpty @Size(max = 500) List<VehicleCreateRequest> vehicles
    ) {
    }

    public record VehicleCreateResult(String vehicleId, String cityCode, String orgId) {
    }

    public record VehicleBatchSkip(int rowNumber, String vehicleId, String reason) {
    }

    public record VehicleBatchResult(
            int requestedCount,
            int createdCount,
            int skippedCount,
            List<VehicleBatchSkip> skipped
    ) {
    }
}

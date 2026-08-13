package cn.bike.platform.city;

import cn.bike.platform.admin.AdminModels.RecordStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public final class CityModels {

    private CityModels() {
    }

    public record City(
            String cityCode,
            String cityName,
            String orgId,
            String orgName,
            BigDecimal centerLongitude,
            BigDecimal centerLatitude,
            BigDecimal minLongitude,
            BigDecimal minLatitude,
            BigDecimal maxLongitude,
            BigDecimal maxLatitude,
            RecordStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record CityRequest(
            @NotBlank @Pattern(regexp = "^[0-9]{6}$", message = "cityCode 必须是 6 位行政区代码") String cityCode,
            @NotBlank @Size(max = 64) String cityName,
            @NotBlank @Size(max = 36) String orgId,
            @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal centerLongitude,
            @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal centerLatitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal minLongitude,
            @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal minLatitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal maxLongitude,
            @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal maxLatitude,
            @NotNull RecordStatus status
    ) {
    }
}

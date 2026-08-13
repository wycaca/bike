package cn.bike.platform.report.revenue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class RevenueReportModels {

    private RevenueReportModels() {
    }

    public enum RevenueGranularity {
        DAY,
        MONTH
    }

    /** 金额、运力和单位经济指标。金额单位为元, 比率单位为百分比。 */
    public record RevenueValues(
            BigDecimal grossBookings,
            BigDecimal discountAmount,
            BigDecimal refundAmount,
            BigDecimal netRevenue,
            long completedRides,
            long activeVehicles,
            long vehicleDays,
            BigDecimal averageDeployedVehicles,
            BigDecimal ridesPerVehicleDay,
            BigDecimal averageRevenuePerRide,
            BigDecimal revenuePerVehicleDay,
            BigDecimal discountRate,
            BigDecimal refundRate,
            BigDecimal averageRideDurationMinutes,
            BigDecimal averageRideDistanceKm
    ) {
    }

    public record RevenueSummary(
            LocalDate fromDate,
            LocalDate toDate,
            RevenueValues values
    ) {
    }

    public record RevenuePeriod(
            LocalDate periodStart,
            LocalDate periodEnd,
            RevenueValues values
    ) {
    }

    public record RevenueReport(
            String cityCode,
            RevenueGranularity granularity,
            RevenueSummary summary,
            List<RevenuePeriod> periods,
            Instant generatedAt
    ) {
    }

    /** 仓储层原始合计, 由服务层统一计算比率和均值。 */
    public record RawMetrics(
            BigDecimal grossBookings,
            BigDecimal discountAmount,
            BigDecimal refundAmount,
            BigDecimal netRevenue,
            long completedRides,
            long activeVehicles,
            long vehicleDays,
            long durationSeconds,
            long distanceMeters
    ) {
    }

    record RawPeriodMetrics(LocalDate periodStart, RawMetrics metrics) {
    }
}

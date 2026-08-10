package cn.bike.platform.dashboard;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class DashboardModels {

    private DashboardModels() {
    }

    public record DashboardSummary(
            long totalVehicles,
            long onlineVehicles,
            long ridingVehicles,
            long offlineVehicles,
            long lowBatteryVehicles,
            long faultVehicles,
            long maintenanceVehicles,
            BigDecimal onlineRate
    ) {
    }

    public record DailyTrend(
            LocalDate date,
            long activeVehicles,
            long telemetryReports,
            BigDecimal averageBattery
    ) {
    }

    public record AreaDistribution(
            String areaCode,
            long vehicleCount,
            long onlineCount,
            long lowBatteryCount,
            long faultCount
    ) {
    }

    public record DashboardData(
            DashboardSummary summary,
            List<DailyTrend> trends,
            List<AreaDistribution> areas,
            Instant generatedAt
    ) {
    }

    public record VehicleReportRow(
            String vehicleId,
            String plateNumber,
            String model,
            String cityCode,
            String areaCode,
            String lifecycleStatus,
            Boolean online,
            Integer batteryPercent,
            String controllerStatus,
            Instant reportedAt
    ) {
    }
}

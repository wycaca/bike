package cn.bike.platform.report;

import cn.bike.platform.report.RevenueReportModels.RawMetrics;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface RevenueReportMapper {

    RawMetrics totals(
            @Param("cityCode") String cityCode,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("fromInstant") Instant fromInstant,
            @Param("toInstant") Instant toInstant
    );

    List<PeriodRow> periods(
            @Param("cityCode") String cityCode,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("fromInstant") Instant fromInstant,
            @Param("toInstant") Instant toInstant,
            @Param("granularity") String granularity
    );

    record PeriodRow(
            LocalDate periodStart,
            BigDecimal grossBookings,
            BigDecimal discountAmount,
            BigDecimal refundAmount,
            BigDecimal netRevenue,
            long completedRides,
            long activeVehicles,
            long durationSeconds,
            long distanceMeters,
            long vehicleDays
    ) {
    }
}

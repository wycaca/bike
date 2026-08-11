package cn.bike.platform.report;

import cn.bike.platform.report.RevenueReportModels.RawMetrics;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 收入报表聚合 Mapper, 维护订单收入、骑行次数和车辆运力的统一统计口径.
 * 自然日按 Asia/Shanghai 切分, 空档日期由 SQL 日历序列补齐.
 */
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

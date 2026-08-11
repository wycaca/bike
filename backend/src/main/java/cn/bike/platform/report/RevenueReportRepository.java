package cn.bike.platform.report;

import cn.bike.platform.report.RevenueReportModels.RawMetrics;
import cn.bike.platform.report.RevenueReportModels.RawPeriodMetrics;
import cn.bike.platform.report.RevenueReportModels.RevenueGranularity;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Repository
public class RevenueReportRepository {

    private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Shanghai");
    private final RevenueReportMapper mapper;

    public RevenueReportRepository(RevenueReportMapper mapper) {
        this.mapper = mapper;
    }

    /** 输入: 城市和闭区间日期; 输出: 整个查询周期的原始收入及运力合计. */
    public RawMetrics totals(String cityCode, LocalDate fromDate, LocalDate toDate) {
        return mapper.totals(cityCode, fromDate, toDate, dayStart(fromDate), dayStart(toDate.plusDays(1)));
    }

    /** 输入: 城市、日期和日/月粒度; 输出: 每个周期的原始收入及运力合计. */
    public List<RawPeriodMetrics> periods(
            String cityCode, LocalDate fromDate, LocalDate toDate, RevenueGranularity granularity
    ) {
        return mapper.periods(cityCode, fromDate, toDate, dayStart(fromDate), dayStart(toDate.plusDays(1)),
                        granularity.name())
                .stream()
                .map(row -> new RawPeriodMetrics(row.periodStart(), new RawMetrics(
                        row.grossBookings(), row.discountAmount(), row.refundAmount(), row.netRevenue(),
                        row.completedRides(), row.activeVehicles(), row.vehicleDays(),
                        row.durationSeconds(), row.distanceMeters())))
                .toList();
    }

    private Instant dayStart(LocalDate date) {
        return date.atStartOfDay(REPORT_ZONE).toInstant();
    }
}

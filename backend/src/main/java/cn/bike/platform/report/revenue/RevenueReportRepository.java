package cn.bike.platform.report.revenue;

import cn.bike.platform.report.revenue.RevenueReportModels.RawMetrics;
import cn.bike.platform.report.revenue.RevenueReportModels.RawPeriodMetrics;
import cn.bike.platform.report.revenue.RevenueReportModels.RevenueGranularity;
import cn.bike.platform.security.DataPermission;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * 收入报表仓储, 负责日期边界和数据库聚合结果的领域转换.
 * 闭区间 LocalDate 按 Asia/Shanghai 转为半开 Instant 区间, 避免跨日重复统计.
 */
@Repository
public class RevenueReportRepository {

    private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Shanghai");
    private final RevenueReportMapper mapper;

    public RevenueReportRepository(RevenueReportMapper mapper) {
        this.mapper = mapper;
    }

    /** 输入: 城市和闭区间日期; 输出: 整个查询周期的原始收入及运力合计. */
    public RawMetrics totals(
            String cityCode, LocalDate fromDate, LocalDate toDate, DataPermission permission
    ) {
        return mapper.totals(cityCode, fromDate, toDate, dayStart(fromDate), dayStart(toDate.plusDays(1)),
                permission);
    }

    /** 输入: 城市、日期和日/月粒度; 输出: 每个周期的原始收入及运力合计. */
    public List<RawPeriodMetrics> periods(
            String cityCode, LocalDate fromDate, LocalDate toDate, RevenueGranularity granularity,
            DataPermission permission
    ) {
        return mapper.periods(cityCode, fromDate, toDate, dayStart(fromDate), dayStart(toDate.plusDays(1)),
                        granularity.name(), permission)
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

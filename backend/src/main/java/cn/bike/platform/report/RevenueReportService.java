package cn.bike.platform.report;

import cn.bike.platform.report.RevenueReportModels.RawMetrics;
import cn.bike.platform.report.RevenueReportModels.RevenueGranularity;
import cn.bike.platform.report.RevenueReportModels.RevenuePeriod;
import cn.bike.platform.report.RevenueReportModels.RevenueReport;
import cn.bike.platform.report.RevenueReportModels.RevenueSummary;
import cn.bike.platform.report.RevenueReportModels.RevenueValues;
import cn.bike.platform.security.DataPermission;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.io.IOException;
import java.io.Writer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Service
public class RevenueReportService {

    private final RevenueReportRepository repository;

    public RevenueReportService(RevenueReportRepository repository) {
        this.repository = repository;
    }

    /** 输入: 城市、闭区间日期和粒度; 输出: 收入、利用率及单位经济报表。 */
    public RevenueReport report(
            String cityCode, LocalDate fromDate, LocalDate toDate, RevenueGranularity granularity,
            DataPermission permission
    ) {
        validateRequest(cityCode, fromDate, toDate, granularity);
        var totals = repository.totals(cityCode, fromDate, toDate, permission);
        var periods = repository.periods(cityCode, fromDate, toDate, granularity, permission).stream()
                .map(raw -> {
                    var periodEnd = granularity == RevenueGranularity.MONTH
                            ? earlier(raw.periodStart().plusMonths(1).minusDays(1), toDate)
                            : raw.periodStart();
                    var actualStart = raw.periodStart().isBefore(fromDate) ? fromDate : raw.periodStart();
                    return new RevenuePeriod(actualStart, periodEnd,
                            calculate(raw.metrics(), inclusiveDays(actualStart, periodEnd)));
                }).toList();
        return new RevenueReport(cityCode, granularity,
                new RevenueSummary(fromDate, toDate, calculate(totals, inclusiveDays(fromDate, toDate))),
                periods, Instant.now());
    }

    /** 输入: 字符流和报表条件; 输出: 流式写入的 CSV 数据行数。 */
    public long writeCsv(
            Writer writer,
            String cityCode,
            LocalDate fromDate,
            LocalDate toDate,
            RevenueGranularity granularity,
            DataPermission permission
    ) throws IOException {
        var report = report(cityCode, fromDate, toDate, granularity, permission);
        writer.write("\uFEFF周期,总流水(元),优惠(元),退款(元),净收入(元),有效订单,活跃车辆,平均投放车辆,"
                + "单车日均骑行次数(RpD),单均收入(元),单车日均收入(元),优惠率(%),退款率(%),平均时长(分钟),平均距离(公里)\r\n");
        for (var period : report.periods()) appendRow(writer, period);
        return report.periods().size();
    }

    /**
     * 输入: 原始金额、订单和车辆日数及周期天数; 输出: 可直接展示的单位经济指标。
     *
     * 步骤:
     * 1. RpD 使用有效订单除以投放车辆日数, 反映车辆周转效率。
     * 2. 单均收入使用净收入除以有效订单, 单车日均收入使用净收入除以投放车辆日数。
     * 3. 退款率以扣除优惠后的实付前金额为基数, 避免把优惠误计为退款。
     */
    RevenueValues calculate(RawMetrics raw, long days) {
        var gross = money(raw.grossBookings());
        var discount = money(raw.discountAmount());
        var refund = money(raw.refundAmount());
        var net = money(raw.netRevenue());
        var paidBeforeRefund = gross.subtract(discount).max(BigDecimal.ZERO);
        return new RevenueValues(gross, discount, refund, net,
                raw.completedRides(), raw.activeVehicles(), raw.vehicleDays(),
                divide(raw.vehicleDays(), days, 1),
                divide(raw.completedRides(), raw.vehicleDays(), 2),
                divide(net, raw.completedRides(), 2),
                divide(net, raw.vehicleDays(), 2),
                percent(discount, gross), percent(refund, paidBeforeRefund),
                divide(raw.durationSeconds(), raw.completedRides() * 60L, 1),
                divide(raw.distanceMeters(), raw.completedRides() * 1000L, 2));
    }

    private void appendRow(Writer writer, RevenuePeriod period) throws IOException {
        var value = period.values();
        var label = period.periodStart().equals(period.periodEnd())
                ? period.periodStart().toString()
                : period.periodStart() + "~" + period.periodEnd();
        var row = new StringBuilder(label).append(',').append(value.grossBookings()).append(',')
                .append(value.discountAmount()).append(',').append(value.refundAmount()).append(',')
                .append(value.netRevenue()).append(',').append(value.completedRides()).append(',')
                .append(value.activeVehicles()).append(',').append(value.averageDeployedVehicles()).append(',')
                .append(value.ridesPerVehicleDay()).append(',').append(value.averageRevenuePerRide()).append(',')
                .append(value.revenuePerVehicleDay()).append(',').append(value.discountRate()).append(',')
                .append(value.refundRate()).append(',').append(value.averageRideDurationMinutes()).append(',')
                .append(value.averageRideDistanceKm()).append("\r\n");
        writer.write(row.toString());
    }

    private long inclusiveDays(LocalDate start, LocalDate end) {
        return ChronoUnit.DAYS.between(start, end) + 1;
    }

    private LocalDate earlier(LocalDate first, LocalDate second) {
        return first.isBefore(second) ? first : second;
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal percent(BigDecimal numerator, BigDecimal denominator) {
        if (denominator.signum() == 0) return BigDecimal.ZERO.setScale(2);
        return numerator.multiply(BigDecimal.valueOf(100)).divide(denominator, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal divide(long numerator, long denominator, int scale) {
        return divide(BigDecimal.valueOf(numerator), denominator, scale);
    }

    private BigDecimal divide(BigDecimal numerator, long denominator, int scale) {
        if (denominator == 0) return BigDecimal.ZERO.setScale(scale);
        return numerator.divide(BigDecimal.valueOf(denominator), scale, RoundingMode.HALF_UP);
    }

    void validateRequest(
            String cityCode, LocalDate fromDate, LocalDate toDate, RevenueGranularity granularity
    ) {
        if (cityCode == null || !cityCode.matches("^[0-9]{6}$")) {
            throw new IllegalArgumentException("cityCode 必须是 6 位行政区代码");
        }
        if (fromDate == null || toDate == null || granularity == null) {
            throw new IllegalArgumentException("日期范围和统计粒度不能为空");
        }
        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("开始日期不能晚于结束日期");
        }
        if (inclusiveDays(fromDate, toDate) > 366) {
            throw new IllegalArgumentException("单次查询范围不能超过 366 天");
        }
    }
}

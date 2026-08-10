package cn.bike.platform.report;

import cn.bike.platform.report.RevenueReportModels.RawMetrics;
import cn.bike.platform.report.RevenueReportModels.RawPeriodMetrics;
import cn.bike.platform.report.RevenueReportModels.RevenueGranularity;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Repository
public class RevenueReportRepository {

    private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Shanghai");
    private final JdbcClient jdbcClient;

    public RevenueReportRepository(@Qualifier("reportingJdbcClient") JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /** 输入: 城市和闭区间日期; 输出: 整个查询周期的原始收入及运力合计。 */
    public RawMetrics totals(String cityCode, LocalDate fromDate, LocalDate toDate) {
        return jdbcClient.sql("""
                        WITH calendar AS (
                            SELECT day::date AS report_date
                            FROM generate_series(CAST(:fromDate AS date), CAST(:toDate AS date), interval '1 day') day
                        ), fleet_daily AS (
                            SELECT c.report_date, count(v.vehicle_id) AS deployed_vehicles
                            FROM calendar c LEFT JOIN vehicle v
                              ON v.operation_city_code = :cityCode
                             AND v.launch_date <= c.report_date
                             AND v.lifecycle_status IN ('OPERATING', 'DISPATCHING')
                            GROUP BY c.report_date
                        ), order_metrics AS (
                            SELECT coalesce(sum(gross_amount) FILTER (WHERE order_status <> 'CANCELLED'), 0) AS gross,
                                   coalesce(sum(discount_amount) FILTER (WHERE order_status <> 'CANCELLED'), 0) AS discount,
                                   coalesce(sum(refund_amount) FILTER (WHERE order_status <> 'CANCELLED'), 0) AS refund,
                                   coalesce(sum(net_revenue) FILTER (WHERE order_status <> 'CANCELLED'), 0) AS net,
                                   count(*) FILTER (WHERE order_status <> 'CANCELLED') AS completed_rides,
                                   count(DISTINCT vehicle_id) FILTER (WHERE order_status <> 'CANCELLED') AS active_vehicles,
                                   coalesce(sum(duration_seconds) FILTER (WHERE order_status <> 'CANCELLED'), 0) AS duration_seconds,
                                   coalesce(sum(distance_meters) FILTER (WHERE order_status <> 'CANCELLED'), 0) AS distance_meters
                            FROM ride_order
                            WHERE city_code = :cityCode AND started_at >= :fromInstant AND started_at < :toInstant
                        )
                        SELECT o.*, coalesce(sum(f.deployed_vehicles), 0) AS vehicle_days
                        FROM order_metrics o CROSS JOIN fleet_daily f
                        GROUP BY o.gross, o.discount, o.refund, o.net, o.completed_rides,
                                 o.active_vehicles, o.duration_seconds, o.distance_meters
                        """)
                .param("cityCode", cityCode)
                .param("fromDate", fromDate).param("toDate", toDate)
                .param("fromInstant", dayStart(fromDate)).param("toInstant", dayStart(toDate.plusDays(1)))
                .query((rs, rowNum) -> rawMetrics(rs)).single();
    }

    /** 输入: 城市、日期和日/月粒度; 输出: 每个周期的原始收入及运力合计。 */
    public List<RawPeriodMetrics> periods(
            String cityCode, LocalDate fromDate, LocalDate toDate, RevenueGranularity granularity
    ) {
        var calendarBucket = bucket("c.report_date", granularity);
        var orderBucket = bucket("(started_at AT TIME ZONE 'Asia/Shanghai')::date", granularity);
        var sql = """
                WITH calendar AS (
                    SELECT day::date AS report_date
                    FROM generate_series(CAST(:fromDate AS date), CAST(:toDate AS date), interval '1 day') day
                ), fleet_daily AS (
                    SELECT c.report_date, count(v.vehicle_id) AS deployed_vehicles
                    FROM calendar c LEFT JOIN vehicle v
                      ON v.operation_city_code = :cityCode
                     AND v.launch_date <= c.report_date
                     AND v.lifecycle_status IN ('OPERATING', 'DISPATCHING')
                    GROUP BY c.report_date
                ), periods AS (
                    SELECT DISTINCT %s AS period_start FROM calendar c
                ), fleet_metrics AS (
                    SELECT %s AS period_start, sum(f.deployed_vehicles) AS vehicle_days
                    FROM fleet_daily f JOIN calendar c ON c.report_date = f.report_date
                    GROUP BY period_start
                ), order_metrics AS (
                    SELECT %s AS period_start,
                           coalesce(sum(gross_amount) FILTER (WHERE order_status <> 'CANCELLED'), 0) AS gross,
                           coalesce(sum(discount_amount) FILTER (WHERE order_status <> 'CANCELLED'), 0) AS discount,
                           coalesce(sum(refund_amount) FILTER (WHERE order_status <> 'CANCELLED'), 0) AS refund,
                           coalesce(sum(net_revenue) FILTER (WHERE order_status <> 'CANCELLED'), 0) AS net,
                           count(*) FILTER (WHERE order_status <> 'CANCELLED') AS completed_rides,
                           count(DISTINCT vehicle_id) FILTER (WHERE order_status <> 'CANCELLED') AS active_vehicles,
                           coalesce(sum(duration_seconds) FILTER (WHERE order_status <> 'CANCELLED'), 0) AS duration_seconds,
                           coalesce(sum(distance_meters) FILTER (WHERE order_status <> 'CANCELLED'), 0) AS distance_meters
                    FROM ride_order
                    WHERE city_code = :cityCode AND started_at >= :fromInstant AND started_at < :toInstant
                    GROUP BY period_start
                )
                SELECT p.period_start, coalesce(o.gross, 0) AS gross,
                       coalesce(o.discount, 0) AS discount, coalesce(o.refund, 0) AS refund,
                       coalesce(o.net, 0) AS net, coalesce(o.completed_rides, 0) AS completed_rides,
                       coalesce(o.active_vehicles, 0) AS active_vehicles,
                       coalesce(o.duration_seconds, 0) AS duration_seconds,
                       coalesce(o.distance_meters, 0) AS distance_meters,
                       coalesce(f.vehicle_days, 0) AS vehicle_days
                FROM periods p LEFT JOIN order_metrics o ON o.period_start = p.period_start
                LEFT JOIN fleet_metrics f ON f.period_start = p.period_start
                ORDER BY p.period_start
                """.formatted(calendarBucket, calendarBucket, orderBucket);
        return jdbcClient.sql(sql)
                .param("cityCode", cityCode)
                .param("fromDate", fromDate).param("toDate", toDate)
                .param("fromInstant", dayStart(fromDate)).param("toInstant", dayStart(toDate.plusDays(1)))
                .query((rs, rowNum) -> new RawPeriodMetrics(
                        rs.getObject("period_start", LocalDate.class), rawMetrics(rs))).list();
    }

    private String bucket(String dateExpression, RevenueGranularity granularity) {
        if (granularity == RevenueGranularity.MONTH) {
            return "date_trunc('month', " + dateExpression + ")::date";
        }
        return dateExpression;
    }

    private java.sql.Timestamp dayStart(LocalDate date) {
        return java.sql.Timestamp.from(date.atStartOfDay(REPORT_ZONE).toInstant());
    }

    private RawMetrics rawMetrics(ResultSet rs) throws SQLException {
        return new RawMetrics(decimal(rs, "gross"), decimal(rs, "discount"), decimal(rs, "refund"),
                decimal(rs, "net"), rs.getLong("completed_rides"), rs.getLong("active_vehicles"),
                rs.getLong("vehicle_days"), rs.getLong("duration_seconds"), rs.getLong("distance_meters"));
    }

    private BigDecimal decimal(ResultSet rs, String column) throws SQLException {
        var value = rs.getBigDecimal(column);
        return value == null ? BigDecimal.ZERO : value;
    }
}

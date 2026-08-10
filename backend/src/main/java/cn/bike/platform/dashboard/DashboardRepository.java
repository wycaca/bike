package cn.bike.platform.dashboard;

import cn.bike.platform.dashboard.DashboardModels.AreaDistribution;
import cn.bike.platform.dashboard.DashboardModels.DailyTrend;
import cn.bike.platform.dashboard.DashboardModels.DashboardSummary;
import cn.bike.platform.dashboard.DashboardModels.VehicleReportRow;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Repository
public class DashboardRepository {

    private final JdbcClient jdbcClient;

    public DashboardRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /** 输入: 城市代码; 输出: 车辆运营核心指标。 */
    public DashboardSummary summary(String cityCode) {
        return jdbcClient.sql("""
                        SELECT count(*) AS total,
                               count(*) FILTER (WHERE l.online) AS online_count,
                               count(*) FILTER (WHERE l.ride_status = 'RIDING') AS riding_count,
                               count(*) FILTER (WHERE l.online = false OR l.vehicle_id IS NULL) AS offline_count,
                               count(*) FILTER (WHERE l.battery_percent < 20) AS low_battery_count,
                               count(*) FILTER (WHERE l.controller_status = 'FAULT'
                                   OR jsonb_array_length(l.fault_codes) > 0) AS fault_count,
                               count(*) FILTER (WHERE v.lifecycle_status = 'MAINTENANCE') AS maintenance_count
                        FROM vehicle v LEFT JOIN vehicle_latest l ON l.vehicle_id = v.vehicle_id
                        WHERE v.operation_city_code = :cityCode
                        """)
                .param("cityCode", cityCode)
                .query((rs, rowNum) -> {
                    var total = rs.getLong("total");
                    var online = rs.getLong("online_count");
                    var rate = total == 0 ? BigDecimal.ZERO
                            : BigDecimal.valueOf(online * 100.0 / total).setScale(1, RoundingMode.HALF_UP);
                    return new DashboardSummary(total, online, rs.getLong("riding_count"),
                            rs.getLong("offline_count"), rs.getLong("low_battery_count"),
                            rs.getLong("fault_count"), rs.getLong("maintenance_count"), rate);
                }).single();
    }

    /** 输入: 城市代码和天数; 输出: 按中国时区统计的每日遥测趋势。 */
    public List<DailyTrend> trends(String cityCode, int days) {
        return jdbcClient.sql("""
                        SELECT (p.reported_at AT TIME ZONE 'Asia/Shanghai')::date AS report_date,
                               count(DISTINCT p.vehicle_id) AS active_vehicles,
                               count(*) AS telemetry_reports,
                               round(avg(p.battery_percent)::numeric, 1) AS average_battery
                        FROM vehicle_position p JOIN vehicle v ON v.vehicle_id = p.vehicle_id
                        WHERE v.operation_city_code = :cityCode
                          AND p.reported_at >= now() - (:days * interval '1 day')
                        GROUP BY report_date ORDER BY report_date
                        """)
                .param("cityCode", cityCode).param("days", days)
                .query((rs, rowNum) -> new DailyTrend(rs.getObject("report_date", java.time.LocalDate.class),
                        rs.getLong("active_vehicles"), rs.getLong("telemetry_reports"),
                        rs.getBigDecimal("average_battery"))).list();
    }

    /** 输入: 城市代码; 输出: 各运营区域车辆状态分布。 */
    public List<AreaDistribution> areaDistribution(String cityCode) {
        return jdbcClient.sql("""
                        SELECT v.operation_area_code,
                               count(*) AS vehicle_count,
                               count(*) FILTER (WHERE l.online) AS online_count,
                               count(*) FILTER (WHERE l.battery_percent < 20) AS low_battery_count,
                               count(*) FILTER (WHERE l.controller_status = 'FAULT'
                                   OR jsonb_array_length(l.fault_codes) > 0) AS fault_count
                        FROM vehicle v LEFT JOIN vehicle_latest l ON l.vehicle_id = v.vehicle_id
                        WHERE v.operation_city_code = :cityCode
                        GROUP BY v.operation_area_code ORDER BY count(*) DESC
                        """)
                .param("cityCode", cityCode)
                .query((rs, rowNum) -> new AreaDistribution(rs.getString("operation_area_code"),
                        rs.getLong("vehicle_count"), rs.getLong("online_count"),
                        rs.getLong("low_battery_count"), rs.getLong("fault_count"))).list();
    }

    /** 输入: 城市代码; 输出: 用于 CSV 导出的车辆状态明细。 */
    public List<VehicleReportRow> vehicleReport(String cityCode) {
        return jdbcClient.sql("""
                        SELECT v.vehicle_id, v.plate_number, v.model, v.operation_city_code,
                               v.operation_area_code, v.lifecycle_status, l.online,
                               l.battery_percent, l.controller_status, l.reported_at
                        FROM vehicle v LEFT JOIN vehicle_latest l ON l.vehicle_id = v.vehicle_id
                        WHERE v.operation_city_code = :cityCode ORDER BY v.vehicle_id
                        """)
                .param("cityCode", cityCode)
                .query((rs, rowNum) -> new VehicleReportRow(
                        rs.getString("vehicle_id"), rs.getString("plate_number"), rs.getString("model"),
                        rs.getString("operation_city_code"), rs.getString("operation_area_code"),
                        rs.getString("lifecycle_status"), (Boolean) rs.getObject("online"),
                        nullableInteger(rs, "battery_percent"), rs.getString("controller_status"),
                        rs.getTimestamp("reported_at") == null ? null : rs.getTimestamp("reported_at").toInstant()))
                .list();
    }

    /** 输入: 结果集与列名; 输出: 保留 SQL NULL 语义的整数值。 */
    private Integer nullableInteger(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        var value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}

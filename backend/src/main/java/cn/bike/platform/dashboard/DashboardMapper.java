package cn.bike.platform.dashboard;

import cn.bike.platform.dashboard.DashboardModels.AreaDistribution;
import cn.bike.platform.dashboard.DashboardModels.DailyTrend;
import cn.bike.platform.dashboard.DashboardModels.DashboardSummary;
import cn.bike.platform.dashboard.DashboardModels.VehicleReportRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 仪表盘只读查询 Mapper, 集中维护车辆状态和异常报表的数据库统计口径.
 * 聚合在数据库完成, 避免服务实例拉取明细后重复计算.
 */
@Mapper
public interface DashboardMapper {

    @Select("""
            WITH metrics AS (
                SELECT count(*) AS total_vehicles,
                       count(*) FILTER (WHERE l.online) AS online_vehicles,
                       count(*) FILTER (WHERE l.ride_status = 'RIDING') AS riding_vehicles,
                       count(*) FILTER (WHERE l.online = false OR l.vehicle_id IS NULL) AS offline_vehicles,
                       count(*) FILTER (WHERE l.battery_percent < 20) AS low_battery_vehicles,
                       count(*) FILTER (WHERE l.controller_status = 'FAULT'
                           OR jsonb_array_length(l.fault_codes) > 0) AS fault_vehicles,
                       count(*) FILTER (WHERE v.lifecycle_status = 'MAINTENANCE') AS maintenance_vehicles
                FROM vehicle v LEFT JOIN vehicle_latest l ON l.vehicle_id = v.vehicle_id
                WHERE v.operation_city_code = #{cityCode}
            )
            SELECT *, CASE WHEN total_vehicles = 0 THEN 0
                       ELSE round(online_vehicles * 100.0 / total_vehicles, 1) END AS online_rate
            FROM metrics
            """)
    DashboardSummary summary(@Param("cityCode") String cityCode);

    @Select("""
            SELECT (p.reported_at AT TIME ZONE 'Asia/Shanghai')::date AS date,
                   count(DISTINCT p.vehicle_id) AS active_vehicles,
                   count(*) AS telemetry_reports,
                   round(avg(p.battery_percent)::numeric, 1) AS average_battery
            FROM vehicle_position p JOIN vehicle v ON v.vehicle_id = p.vehicle_id
            WHERE v.operation_city_code = #{cityCode}
              AND p.reported_at >= now() - (#{days} * interval '1 day')
            GROUP BY date ORDER BY date
            """)
    List<DailyTrend> trends(@Param("cityCode") String cityCode, @Param("days") int days);

    @Select("""
            SELECT v.operation_area_code AS area_code,
                   count(*) AS vehicle_count,
                   count(*) FILTER (WHERE l.online) AS online_count,
                   count(*) FILTER (WHERE l.battery_percent < 20) AS low_battery_count,
                   count(*) FILTER (WHERE l.controller_status = 'FAULT'
                       OR jsonb_array_length(l.fault_codes) > 0) AS fault_count
            FROM vehicle v LEFT JOIN vehicle_latest l ON l.vehicle_id = v.vehicle_id
            WHERE v.operation_city_code = #{cityCode}
            GROUP BY v.operation_area_code ORDER BY count(*) DESC
            """)
    List<AreaDistribution> areaDistribution(@Param("cityCode") String cityCode);

    @Select("""
            SELECT v.vehicle_id, v.plate_number, v.model, v.operation_city_code AS city_code,
                   v.operation_area_code AS area_code, v.lifecycle_status, l.online,
                   l.battery_percent, l.controller_status, l.reported_at
            FROM vehicle v LEFT JOIN vehicle_latest l ON l.vehicle_id = v.vehicle_id
            WHERE v.operation_city_code = #{cityCode} ORDER BY v.vehicle_id
            """)
    List<VehicleReportRow> vehicleReport(@Param("cityCode") String cityCode);
}

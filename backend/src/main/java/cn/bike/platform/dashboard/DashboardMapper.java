package cn.bike.platform.dashboard;

import cn.bike.platform.dashboard.DashboardModels.AreaDistribution;
import cn.bike.platform.dashboard.DashboardModels.DailyTrend;
import cn.bike.platform.dashboard.DashboardModels.DashboardSummary;
import cn.bike.platform.dashboard.DashboardModels.VehicleReportRow;
import cn.bike.platform.security.DataPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 仪表盘只读查询 Mapper, 集中维护车辆状态和异常报表的数据库统计口径.
 * 聚合在数据库完成, 避免服务实例拉取明细后重复计算.
 */
@Mapper
public interface DashboardMapper {

    DashboardSummary summary(@Param("cityCode") String cityCode, @Param("permission") DataPermission permission);

    List<DailyTrend> trends(
            @Param("cityCode") String cityCode,
            @Param("days") int days,
            @Param("permission") DataPermission permission
    );

    List<AreaDistribution> areaDistribution(
            @Param("cityCode") String cityCode,
            @Param("permission") DataPermission permission
    );

    List<VehicleReportRow> vehicleReport(
            @Param("cityCode") String cityCode,
            @Param("permission") DataPermission permission
    );
}

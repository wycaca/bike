package cn.bike.platform.dashboard;

import cn.bike.platform.dashboard.DashboardModels.AreaDistribution;
import cn.bike.platform.dashboard.DashboardModels.DailyTrend;
import cn.bike.platform.dashboard.DashboardModels.DashboardSummary;
import cn.bike.platform.dashboard.DashboardModels.VehicleReportRow;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 仪表盘查询仓储, 为服务层提供稳定的统计行模型.
 * 该层不重新聚合数据, 统计口径以 Mapper SQL 为唯一来源.
 */
@Repository
public class DashboardRepository {

    private final DashboardMapper mapper;

    public DashboardRepository(DashboardMapper mapper) {
        this.mapper = mapper;
    }

    /** 输入: 城市代码; 输出: 车辆运营核心指标。 */
    public DashboardSummary summary(String cityCode) {
        return mapper.summary(cityCode);
    }

    /** 输入: 城市代码和天数; 输出: 按中国时区统计的每日遥测趋势。 */
    public List<DailyTrend> trends(String cityCode, int days) {
        return mapper.trends(cityCode, days);
    }

    /** 输入: 城市代码; 输出: 各运营区域车辆状态分布。 */
    public List<AreaDistribution> areaDistribution(String cityCode) {
        return mapper.areaDistribution(cityCode);
    }

    /** 输入: 城市代码; 输出: 用于 CSV 导出的车辆状态明细。 */
    public List<VehicleReportRow> vehicleReport(String cityCode) {
        return mapper.vehicleReport(cityCode);
    }
}

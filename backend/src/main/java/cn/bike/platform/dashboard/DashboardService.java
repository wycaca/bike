package cn.bike.platform.dashboard;

import cn.bike.platform.dashboard.DashboardModels.DashboardData;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class DashboardService {

    private final DashboardRepository repository;

    public DashboardService(DashboardRepository repository) {
        this.repository = repository;
    }

    /** 输入: 城市代码和趋势天数; 输出: 看板聚合数据。 */
    public DashboardData dashboard(String cityCode, int days) {
        validate(cityCode, days);
        return new DashboardData(repository.summary(cityCode), repository.trends(cityCode, days),
                repository.areaDistribution(cityCode), Instant.now());
    }

    private void validate(String cityCode, int days) {
        if (cityCode == null || !cityCode.matches("^[0-9]{6}$")) {
            throw new IllegalArgumentException("cityCode 必须是 6 位行政区代码");
        }
        if (days < 1 || days > 31) {
            throw new IllegalArgumentException("days 必须在 1 到 31 之间");
        }
    }
}

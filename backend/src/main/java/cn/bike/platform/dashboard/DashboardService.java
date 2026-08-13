package cn.bike.platform.dashboard;

import cn.bike.platform.dashboard.DashboardModels.DashboardData;
import org.springframework.stereotype.Service;
import cn.bike.platform.security.DataPermissionService;
import cn.bike.platform.security.PlatformPrincipal;

import java.time.Instant;

@Service
public class DashboardService {

    private final DashboardRepository repository;
    private final DataPermissionService dataPermissionService;

    public DashboardService(DashboardRepository repository, DataPermissionService dataPermissionService) {
        this.repository = repository;
        this.dataPermissionService = dataPermissionService;
    }

    /** 输入: 城市代码和趋势天数; 输出: 看板聚合数据。 */
    public DashboardData dashboard(String cityCode, int days, PlatformPrincipal principal) {
        validate(cityCode, days);
        var permission = dataPermissionService.resolve(principal);
        return new DashboardData(repository.summary(cityCode, permission), repository.trends(cityCode, days, permission),
                repository.areaDistribution(cityCode, permission), Instant.now());
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

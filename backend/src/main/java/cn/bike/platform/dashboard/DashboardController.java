package cn.bike.platform.dashboard;

import cn.bike.platform.common.ApiResponse;
import cn.bike.platform.dashboard.DashboardModels.DashboardData;
import cn.bike.platform.security.PlatformAccessPolicy;
import cn.bike.platform.security.PlatformPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/v1")
public class DashboardController {

    private final DashboardService service;
    private final PlatformAccessPolicy accessPolicy;

    public DashboardController(DashboardService service, PlatformAccessPolicy accessPolicy) {
        this.service = service;
        this.accessPolicy = accessPolicy;
    }

    /** 输入: 城市和趋势天数; 输出: 运营看板数据。 */
    @GetMapping("/dashboard")
    public ApiResponse<DashboardData> dashboard(
            @RequestParam String cityCode,
            @RequestParam(defaultValue = "7") int days,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        accessPolicy.requireCity(principal, cityCode);
        return ApiResponse.ok(service.dashboard(cityCode, days));
    }

}

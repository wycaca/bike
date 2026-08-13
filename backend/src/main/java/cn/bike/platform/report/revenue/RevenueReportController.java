package cn.bike.platform.report.revenue;

import cn.bike.platform.common.ApiResponse;
import cn.bike.platform.report.revenue.RevenueReportModels.RevenueGranularity;
import cn.bike.platform.report.revenue.RevenueReportModels.RevenueReport;
import cn.bike.platform.security.DataPermissionService;
import cn.bike.platform.security.PlatformPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;

@RestController
@RequestMapping("/api/v1/reports")
public class RevenueReportController {

    private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Shanghai");
    private final RevenueReportService service;
    private final DataPermissionService dataPermissionService;

    public RevenueReportController(RevenueReportService service, DataPermissionService dataPermissionService) {
        this.service = service;
        this.dataPermissionService = dataPermissionService;
    }

    /** 输入: 城市、可选日期和粒度; 输出: 默认截至昨日的 30 天收入报表。 */
    @GetMapping("/revenue")
    public ApiResponse<RevenueReport> report(
            @RequestParam String cityCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "DAY") RevenueGranularity granularity,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        var range = defaultRange(fromDate, toDate);
        return ApiResponse.ok(service.report(cityCode, range.fromDate(), range.toDate(), granularity,
                dataPermissionService.resolve(principal)));
    }

    private DateRange defaultRange(LocalDate fromDate, LocalDate toDate) {
        var yesterday = LocalDate.now(REPORT_ZONE).minusDays(1);
        var actualTo = toDate == null ? yesterday : toDate;
        var actualFrom = fromDate == null ? actualTo.minusDays(29) : fromDate;
        return new DateRange(actualFrom, actualTo);
    }

    private record DateRange(LocalDate fromDate, LocalDate toDate) {
    }
}

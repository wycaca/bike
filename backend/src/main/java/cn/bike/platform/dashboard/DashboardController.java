package cn.bike.platform.dashboard;

import cn.bike.platform.common.ApiResponse;
import cn.bike.platform.dashboard.DashboardModels.DashboardData;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import cn.bike.platform.security.PlatformPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    /** 输入: 城市和趋势天数; 输出: 运营看板数据。 */
    @GetMapping("/dashboard")
    public ApiResponse<DashboardData> dashboard(
            @RequestParam String cityCode,
            @RequestParam(defaultValue = "7") int days,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ApiResponse.ok(service.dashboard(cityCode, days, principal));
    }

    /** 输入: 城市代码; 输出: UTF-8 CSV 下载响应。 */
    @GetMapping("/reports/vehicle-status.csv")
    public ResponseEntity<byte[]> vehicleStatusCsv(
            @RequestParam String cityCode,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        var filename = "vehicle-status-" + cityCode + "-" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(service.vehicleStatusCsv(cityCode, principal));
    }
}

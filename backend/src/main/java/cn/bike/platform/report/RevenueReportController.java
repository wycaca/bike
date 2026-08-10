package cn.bike.platform.report;

import cn.bike.platform.common.ApiResponse;
import cn.bike.platform.report.RevenueReportModels.RevenueGranularity;
import cn.bike.platform.report.RevenueReportModels.RevenueReport;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;

@RestController
@RequestMapping("/api/v1/reports")
public class RevenueReportController {

    private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Shanghai");
    private final RevenueReportService service;

    public RevenueReportController(RevenueReportService service) {
        this.service = service;
    }

    /** 输入: 城市、可选日期和粒度; 输出: 默认截至昨日的 30 天收入报表。 */
    @GetMapping("/revenue")
    public ApiResponse<RevenueReport> report(
            @RequestParam String cityCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "DAY") RevenueGranularity granularity
    ) {
        var range = defaultRange(fromDate, toDate);
        return ApiResponse.ok(service.report(cityCode, range.fromDate(), range.toDate(), granularity));
    }

    /** 输入: 与查询接口相同的筛选条件; 输出: UTF-8 CSV 下载响应。 */
    @GetMapping("/revenue.csv")
    public ResponseEntity<byte[]> csv(
            @RequestParam String cityCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "DAY") RevenueGranularity granularity
    ) {
        var range = defaultRange(fromDate, toDate);
        var filename = "revenue-" + cityCode + "-" + range.fromDate() + "-" + range.toDate() + ".csv";
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(service.csv(cityCode, range.fromDate(), range.toDate(), granularity));
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

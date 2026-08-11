package cn.bike.platform.dashboard;

import cn.bike.platform.dashboard.DashboardModels.DashboardData;
import cn.bike.platform.dashboard.DashboardModels.VehicleReportRow;
import org.springframework.stereotype.Service;
import cn.bike.platform.security.DataPermissionService;
import cn.bike.platform.security.PlatformPrincipal;

import java.nio.charset.StandardCharsets;
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

    /** 输入: 城市代码; 输出: 带 UTF-8 BOM 的车辆状态 CSV 字节。 */
    public byte[] vehicleStatusCsv(String cityCode, PlatformPrincipal principal) {
        validate(cityCode, 7);
        var permission = dataPermissionService.resolve(principal);
        var csv = new StringBuilder("\uFEFF车辆编号,车牌,车型,城市,运营区,生命周期,在线,电量,控制器状态,最后上报时间\r\n");
        repository.vehicleReport(cityCode, permission).forEach(row -> appendRow(csv, row));
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void appendRow(StringBuilder csv, VehicleReportRow row) {
        csv.append(csv(row.vehicleId())).append(',')
                .append(csv(row.plateNumber())).append(',')
                .append(csv(row.model())).append(',')
                .append(csv(row.cityCode())).append(',')
                .append(csv(row.areaCode())).append(',')
                .append(csv(row.lifecycleStatus())).append(',')
                .append(row.online() == null ? "" : row.online() ? "是" : "否").append(',')
                .append(row.batteryPercent() == null ? "" : row.batteryPercent()).append(',')
                .append(csv(row.controllerStatus())).append(',')
                .append(row.reportedAt() == null ? "" : row.reportedAt()).append("\r\n");
    }

    /** 输入: CSV 字段; 输出: 正确转义后的字段。 */
    static String csv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
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

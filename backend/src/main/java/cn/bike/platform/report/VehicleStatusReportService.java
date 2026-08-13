package cn.bike.platform.report;

import cn.bike.platform.report.VehicleStatusReportMapper.VehicleStatusRow;
import cn.bike.platform.security.DataPermission;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

@Service
@Profile("report-worker")
public class VehicleStatusReportService {

    private final VehicleStatusReportMapper mapper;

    public VehicleStatusReportService(VehicleStatusReportMapper mapper) {
        this.mapper = mapper;
    }

    /** 输入: 字符流、城市代码和数据权限; 输出: 流式写入的可见车辆数据行数。 */
    public long writeCsv(Writer writer, String cityCode, DataPermission permission) throws IOException {
        if (cityCode == null || !cityCode.matches("^[0-9]{6}$")) {
            throw new IllegalArgumentException("cityCode 必须是 6 位行政区代码");
        }
        writer.write("\uFEFF车辆编号,车牌,车型,城市,运营区,生命周期,在线,电量,控制器状态,最后上报时间\r\n");
        var orgIds = permission.orgIds().isEmpty() ? List.of("__NONE__") : permission.orgIds();
        var rows = mapper.findRows(cityCode, permission.unrestricted(), orgIds);
        for (var row : rows) appendRow(writer, row);
        return rows.size();
    }

    private void appendRow(Writer writer, VehicleStatusRow row) throws IOException {
        writer.write(csv(row.vehicleId()) + ',' + csv(row.plateNumber()) + ',' + csv(row.model()) + ','
                + csv(row.cityCode()) + ',' + csv(row.areaCode()) + ',' + csv(row.lifecycleStatus()) + ','
                + (row.online() == null ? "" : row.online() ? "是" : "否") + ','
                + (row.batteryPercent() == null ? "" : row.batteryPercent()) + ','
                + csv(row.controllerStatus()) + ',' + (row.reportedAt() == null ? "" : row.reportedAt())
                + "\r\n");
    }

    /** 输入: CSV 字段; 输出: 符合 RFC 4180 的转义字段。 */
    static String csv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}

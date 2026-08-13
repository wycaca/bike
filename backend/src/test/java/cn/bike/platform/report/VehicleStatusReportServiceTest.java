package cn.bike.platform.report;

import cn.bike.platform.report.VehicleStatusReportMapper.VehicleStatusRow;
import cn.bike.platform.admin.AdminModels.DataScope;
import cn.bike.platform.security.DataPermission;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VehicleStatusReportServiceTest {

    @Test
    void 车辆报表应流式写入Utf8Bom并正确转义中文字段() throws Exception {
        var mapper = mock(VehicleStatusReportMapper.class);
        var service = new VehicleStatusReportService(mapper);
        var permission = new DataPermission(DataScope.ORG_ONLY, "ORG-BJ", List.of("ORG-BJ"));
        when(mapper.findRows("110000", false, List.of("ORG-BJ"))).thenReturn(List.of(new VehicleStatusRow(
                "BIKE-001", "京A,001", "通勤\"增强版", "110000", "东城",
                "OPERATING", true, 87, "NORMAL", Instant.parse("2026-08-10T01:02:03Z")
        )));
        var writer = new StringWriter();

        var rows = service.writeCsv(writer, "110000", permission);

        assertThat(rows).isEqualTo(1);
        assertThat(writer.toString()).startsWith("\uFEFF车辆编号")
                .contains("\"京A,001\"")
                .contains("\"通勤\"\"增强版\"");
    }
}

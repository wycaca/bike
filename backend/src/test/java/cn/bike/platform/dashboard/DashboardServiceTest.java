package cn.bike.platform.dashboard;

import cn.bike.platform.dashboard.DashboardModels.VehicleReportRow;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardServiceTest {

    @Test
    void 车辆报表应包含Utf8Bom并正确转义中文字段() {
        var repository = mock(DashboardRepository.class);
        var service = new DashboardService(repository);
        when(repository.vehicleReport("110000")).thenReturn(List.of(new VehicleReportRow(
                "BIKE-001", "京A,001", "通勤\"增强版", "110000", "东城",
                "IN_SERVICE", true, 87, "NORMAL", Instant.parse("2026-08-10T01:02:03Z")
        )));

        var csv = new String(service.vehicleStatusCsv("110000"), StandardCharsets.UTF_8);

        assertThat(csv).startsWith("\uFEFF车辆编号");
        assertThat(csv).contains("\"京A,001\"");
        assertThat(csv).contains("\"通勤\"\"增强版\"");
    }

    @Test
    void 趋势天数超出范围时应拒绝请求() {
        var service = new DashboardService(mock(DashboardRepository.class));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.dashboard("110000", 32))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 到 31");
    }
}

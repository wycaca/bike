package cn.bike.platform.report;

import cn.bike.platform.TestDataPermissions;
import cn.bike.platform.report.RevenueReportModels.RawMetrics;
import cn.bike.platform.report.RevenueReportModels.RawPeriodMetrics;
import cn.bike.platform.report.RevenueReportModels.RevenueGranularity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.io.StringWriter;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RevenueReportServiceTest {

    private static final RawMetrics METRICS = new RawMetrics(
            new BigDecimal("100.00"), new BigDecimal("10.00"), new BigDecimal("5.00"),
            new BigDecimal("85.00"), 20, 8, 50, 18_000, 40_000
    );

    @Test
    void 应按车辆日数计算周转率和单车日均收入() {
        var service = new RevenueReportService(mock(RevenueReportRepository.class));

        var values = service.calculate(METRICS, 5);

        assertThat(values.averageDeployedVehicles()).isEqualByComparingTo("10.0");
        assertThat(values.ridesPerVehicleDay()).isEqualByComparingTo("0.40");
        assertThat(values.averageRevenuePerRide()).isEqualByComparingTo("4.25");
        assertThat(values.revenuePerVehicleDay()).isEqualByComparingTo("1.70");
        assertThat(values.discountRate()).isEqualByComparingTo("10.00");
        assertThat(values.refundRate()).isEqualByComparingTo("5.56");
        assertThat(values.averageRideDurationMinutes()).isEqualByComparingTo("15.0");
        assertThat(values.averageRideDistanceKm()).isEqualByComparingTo("2.00");
    }

    @Test
    void 月报应按实际查询天数计算平均投放车辆() {
        var repository = mock(RevenueReportRepository.class);
        var service = new RevenueReportService(repository);
        var from = LocalDate.of(2026, 7, 15);
        var to = LocalDate.of(2026, 8, 10);
        when(repository.totals("110000", from, to, TestDataPermissions.ALL)).thenReturn(METRICS);
        when(repository.periods("110000", from, to, RevenueGranularity.MONTH,
                TestDataPermissions.ALL)).thenReturn(List.of(
                new RawPeriodMetrics(LocalDate.of(2026, 7, 1), METRICS),
                new RawPeriodMetrics(LocalDate.of(2026, 8, 1), METRICS)
        ));

        var report = service.report("110000", from, to, RevenueGranularity.MONTH, TestDataPermissions.ALL);

        assertThat(report.periods()).hasSize(2);
        assertThat(report.periods().getFirst().periodStart()).isEqualTo(from);
        assertThat(report.periods().getFirst().periodEnd()).isEqualTo(LocalDate.of(2026, 7, 31));
        assertThat(report.periods().getFirst().values().averageDeployedVehicles())
                .isEqualByComparingTo("2.9");
        assertThat(report.periods().getLast().values().averageDeployedVehicles())
                .isEqualByComparingTo("5.0");
    }

    @Test
    void 收入Csv应流式写入中文表头和Utf8Bom() throws Exception {
        var repository = mock(RevenueReportRepository.class);
        var service = new RevenueReportService(repository);
        var date = LocalDate.of(2026, 8, 9);
        when(repository.totals("110000", date, date, TestDataPermissions.ALL)).thenReturn(METRICS);
        when(repository.periods("110000", date, date, RevenueGranularity.DAY, TestDataPermissions.ALL))
                .thenReturn(List.of(new RawPeriodMetrics(date, METRICS)));

        var writer = new StringWriter();
        var rowCount = service.writeCsv(
                writer, "110000", date, date, RevenueGranularity.DAY, TestDataPermissions.ALL);
        var csv = writer.toString();

        assertThat(rowCount).isEqualTo(1);
        assertThat(csv).startsWith("\uFEFF周期,总流水(元)");
        assertThat(csv).contains("单车日均骑行次数(RpD)");
        assertThat(csv).contains("2026-08-09,100.00,10.00,5.00,85.00,20");
    }

    @Test
    void 查询范围超过一年应拒绝请求() {
        var service = new RevenueReportService(mock(RevenueReportRepository.class));

        assertThatThrownBy(() -> service.report("110000", LocalDate.of(2025, 1, 1),
                LocalDate.of(2026, 1, 2), RevenueGranularity.DAY, TestDataPermissions.ALL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("366 天");
    }
}

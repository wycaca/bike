package cn.bike.platform.report;

import cn.bike.platform.common.ConflictException;
import cn.bike.platform.report.ReportExportModels.ExportJob;
import cn.bike.platform.report.ReportExportModels.ExportRequest;
import cn.bike.platform.report.ReportExportModels.ExportStatus;
import cn.bike.platform.report.ReportExportModels.ReportType;
import cn.bike.platform.report.RevenueReportModels.RevenueGranularity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportExportServiceTest {

    private static final LocalDate FROM = LocalDate.of(2026, 7, 1);
    private static final LocalDate TO = LocalDate.of(2026, 7, 31);

    @Test
    void 创建导出时应只持久化等待任务() {
        var repository = mock(ReportExportRepository.class);
        var revenueService = mock(RevenueReportService.class);
        var storage = mock(ReportFileStorage.class);
        var service = new ReportExportService(repository, revenueService, storage);
        var request = request();
        var pending = job(ExportStatus.PENDING);
        when(repository.countActive("USR-ADMIN")).thenReturn(0L);
        when(repository.create(ReportType.REVENUE, "USR-ADMIN", "110000", FROM, TO,
                RevenueGranularity.DAY, "revenue-110000-2026-07-01-2026-07-31.csv")).thenReturn(pending);

        var result = service.create(request, "USR-ADMIN");

        assertThat(result.status()).isEqualTo(ExportStatus.PENDING);
        assertThat(result.downloadable()).isFalse();
        verify(revenueService).validateRequest("110000", FROM, TO, RevenueGranularity.DAY);
    }

    @Test
    void 同一用户已有三个任务时应拒绝继续入队() {
        var repository = mock(ReportExportRepository.class);
        when(repository.countActive("USR-ADMIN")).thenReturn(3L);
        var service = new ReportExportService(repository, mock(RevenueReportService.class),
                mock(ReportFileStorage.class));

        assertThatThrownBy(() -> service.create(request(), "USR-ADMIN"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("3 个报表任务");
    }

    private ExportRequest request() {
        return new ExportRequest(ReportType.REVENUE, "110000", FROM, TO, RevenueGranularity.DAY);
    }

    static ExportJob job(ExportStatus status) {
        return new ExportJob("11111111-1111-1111-1111-111111111111", ReportType.REVENUE, status,
                "USR-ADMIN", "110000", FROM, TO, RevenueGranularity.DAY,
                "revenue.csv", null, null, null, status == ExportStatus.RUNNING ? 1 : 0,
                null, Instant.parse("2026-08-10T00:00:00Z"),
                status == ExportStatus.RUNNING ? Instant.parse("2026-08-10T00:00:01Z") : null,
                null, null);
    }
}

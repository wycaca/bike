package cn.bike.platform.report.export;

import cn.bike.platform.admin.AdminModels.DataScope;
import cn.bike.platform.admin.AdminModels.UserRole;
import cn.bike.platform.common.ConflictException;
import cn.bike.platform.report.export.ReportExportModels.ExportJob;
import cn.bike.platform.report.export.ReportExportModels.ExportRequest;
import cn.bike.platform.report.export.ReportExportModels.ExportStatus;
import cn.bike.platform.report.export.ReportExportModels.ReportType;
import cn.bike.platform.report.revenue.RevenueReportModels.RevenueGranularity;
import cn.bike.platform.report.revenue.RevenueReportService;
import cn.bike.platform.security.PlatformPrincipal;
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
        when(repository.create(ReportType.REVENUE, principal(), "110000", FROM, TO,
                RevenueGranularity.DAY, "revenue-110000-2026-07-01-2026-07-31.csv")).thenReturn(pending);

        var result = service.create(request, principal());

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

        assertThatThrownBy(() -> service.create(request(), principal()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("3 个报表任务");
    }

    private ExportRequest request() {
        return new ExportRequest(ReportType.REVENUE, "110000", FROM, TO, RevenueGranularity.DAY);
    }

    static ExportJob job(ExportStatus status) {
        return new ExportJob("11111111-1111-1111-1111-111111111111", ReportType.REVENUE, status,
                "USR-ADMIN", "ORG-HQ", DataScope.ALL, "110000", FROM, TO, RevenueGranularity.DAY,
                "revenue.csv", null, null, null, status == ExportStatus.RUNNING ? 1 : 0,
                null, Instant.parse("2026-08-10T00:00:00Z"),
                status == ExportStatus.RUNNING ? Instant.parse("2026-08-10T00:00:01Z") : null,
                null, null);
    }

    private static PlatformPrincipal principal() {
        return new PlatformPrincipal("USR-ADMIN", "admin", "encoded", "系统管理员",
                "ORG-HQ", "运营总部", UserRole.ADMIN, DataScope.ALL, true);
    }
}

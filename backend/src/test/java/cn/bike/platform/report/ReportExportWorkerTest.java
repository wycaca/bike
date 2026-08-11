package cn.bike.platform.report;

import cn.bike.platform.TestDataPermissions;
import cn.bike.platform.report.ReportExportModels.ExportStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ReportExportWorkerTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void Worker应先落盘再把任务标记为成功() throws Exception {
        var repository = mock(ReportExportRepository.class);
        var revenueService = mock(RevenueReportService.class);
        var storage = new ReportFileStorage(temporaryDirectory);
        var worker = new ReportExportWorker(
                repository, revenueService, storage, TestDataPermissions.allService());
        var job = ReportExportServiceTest.job(ExportStatus.RUNNING);
        doAnswer(invocation -> {
            java.io.Writer writer = invocation.getArgument(0);
            writer.write("\uFEFF周期,净收入\r\n2026-07,100.00\r\n");
            return 1L;
        }).when(revenueService).writeCsv(any(), eq("110000"), eq(job.fromDate()),
                eq(job.toDate()), eq(job.granularity()), eq(TestDataPermissions.ALL));

        worker.process(job);

        var storageKey = "report-" + job.jobId() + ".csv";
        assertThat(Files.readString(temporaryDirectory.resolve(storageKey))).startsWith("\uFEFF周期");
        assertThat(temporaryDirectory.resolve(storageKey + ".part")).doesNotExist();
        verify(repository).markSucceeded(eq(job.jobId()), eq(storageKey), anyLong(), eq(1L),
                eq(Duration.ofHours(24)));
    }

    @Test
    void 生成异常时应清理临时文件并标记失败() throws Exception {
        var repository = mock(ReportExportRepository.class);
        var revenueService = mock(RevenueReportService.class);
        var storage = new ReportFileStorage(temporaryDirectory);
        var worker = new ReportExportWorker(
                repository, revenueService, storage, TestDataPermissions.allService());
        var job = ReportExportServiceTest.job(ExportStatus.RUNNING);
        doAnswer(invocation -> {
            throw new java.io.IOException("模拟导出失败");
        }).when(revenueService).writeCsv(any(), any(), any(), any(), any(), any());

        worker.process(job);

        assertThat(temporaryDirectory).isEmptyDirectory();
        verify(repository).markFailed(job.jobId(), "模拟导出失败");
    }
}

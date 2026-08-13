package cn.bike.platform.report.export;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

@Profile("report-worker")
@Configuration
@EnableScheduling
public class ReportWorkerConfiguration {
}

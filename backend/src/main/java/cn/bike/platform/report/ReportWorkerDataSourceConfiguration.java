package cn.bike.platform.report;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;

@Configuration(proxyBeanMethods = false)
@Profile("report-worker")
public class ReportWorkerDataSourceConfiguration {

    /** 输入: 主库连接信息; 输出: 仅供任务领取和状态更新使用的小型读写连接池。 */
    @Bean(name = "dataSource", destroyMethod = "close")
    @Primary
    public HikariDataSource queueDataSource(
            @Value("${DB_URL:}") String configuredUrl,
            @Value("${DB_HOST:localhost}") String host,
            @Value("${DB_PORT:5432}") int port,
            @Value("${DB_NAME:bike}") String database,
            @Value("${DB_USER:bike}") String username,
            @Value("${DB_PASSWORD:bike_dev_password}") String password,
            @Value("${DB_POOL_SIZE:1}") int poolSize
    ) {
        if (poolSize < 1) {
            throw new IllegalArgumentException("报表任务队列连接池至少为 1");
        }
        var jdbcUrl = configuredUrl.isBlank()
                ? "jdbc:postgresql://" + host + ":" + port + "/" + database
                : configuredUrl;
        var config = new HikariConfig();
        config.setPoolName("ReportQueuePool");
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(poolSize);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(3_000);
        config.addDataSourceProperty("ApplicationName", "bike-report-worker-queue");
        return new HikariDataSource(config);
    }

    /**
     * 输入: 报表库连接信息、连接上限和 SQL 超时; 输出: 只读的报表查询连接池。
     *
     * 步骤:
     * 1. 未配置 REPORT_DB_URL 时，根据独立的 REPORT_DB_* 参数组装 JDBC 地址。
     * 2. 连接池保持很小，并将连接标记为只读，防止报表查询误写业务数据。
     * 3. 每条连接设置 statement_timeout，避免异常查询长期占用数据库资源。
     */
    @Bean(destroyMethod = "close")
    public HikariDataSource reportingDataSource(
            @Value("${REPORT_DB_URL:}") String configuredUrl,
            @Value("${REPORT_DB_HOST:${DB_HOST:localhost}}") String host,
            @Value("${REPORT_DB_PORT:${DB_PORT:5432}}") int port,
            @Value("${REPORT_DB_NAME:${DB_NAME:bike}}") String database,
            @Value("${REPORT_DB_USER:${DB_USER:bike}}") String username,
            @Value("${REPORT_DB_PASSWORD:${DB_PASSWORD:bike_dev_password}}") String password,
            @Value("${REPORT_DB_POOL_SIZE:1}") int poolSize,
            @Value("${REPORT_STATEMENT_TIMEOUT_MS:120000}") long statementTimeoutMs
    ) {
        if (poolSize < 1 || statementTimeoutMs < 1_000) {
            throw new IllegalArgumentException("报表连接池至少为 1，SQL 超时不得低于 1000 毫秒");
        }
        var jdbcUrl = configuredUrl.isBlank()
                ? "jdbc:postgresql://" + host + ":" + port + "/" + database
                : configuredUrl;
        var config = new HikariConfig();
        config.setPoolName("ReportQueryPool");
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(poolSize);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(3_000);
        config.setReadOnly(true);
        config.setConnectionInitSql("SET statement_timeout TO " + statementTimeoutMs);
        config.addDataSourceProperty("ApplicationName", "bike-report-worker-query");
        return new HikariDataSource(config);
    }

    /** 输入: 独立报表数据源; 输出: 仅供收入聚合查询使用的 JdbcClient。 */
    @Bean("reportingJdbcClient")
    public JdbcClient reportingJdbcClient(
            @Qualifier("reportingDataSource") HikariDataSource reportingDataSource
    ) {
        return JdbcClient.create(reportingDataSource);
    }
}

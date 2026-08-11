package cn.bike.platform.report;

import com.mybatisflex.core.mybatis.FlexConfiguration;
import com.mybatisflex.spring.FlexSqlSessionFactoryBean;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * 报表 Worker 的双数据源 MyBatis-Flex 配置.
 * 读写池只处理任务队列, 只读池只执行收入聚合, 避免长查询占用在线业务连接.
 */
@Configuration(proxyBeanMethods = false)
@Profile("report-worker")
public class ReportWorkerDataSourceConfiguration {

    /** 输入: 主库连接信息; 输出: 仅供任务领取和状态更新使用的小型读写连接池. */
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
        var config = dataSourceConfig("ReportQueuePool", configuredUrl, host, port, database, username, password,
                poolSize);
        config.addDataSourceProperty("ApplicationName", "bike-report-worker-queue");
        return new HikariDataSource(config);
    }

    /**
     * 输入: 报表库连接信息、连接上限和 SQL 超时; 输出: 只读的报表查询连接池.
     *
     * 独立连接池限制聚合查询并发和执行时间, 避免影响任务队列及在线业务.
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
            throw new IllegalArgumentException("报表连接池至少为 1, SQL 超时不得低于 1000 毫秒");
        }
        var config = dataSourceConfig("ReportQueryPool", configuredUrl, host, port, database, username, password,
                poolSize);
        config.setReadOnly(true);
        config.setConnectionInitSql("SET statement_timeout TO " + statementTimeoutMs);
        config.addDataSourceProperty("ApplicationName", "bike-report-worker-query");
        return new HikariDataSource(config);
    }

    /** 输入: 任务队列数据源; 输出: Worker 业务 Mapper 使用的 MyBatis-Flex 会话工厂. */
    @Bean(name = "sqlSessionFactory")
    @Primary
    public SqlSessionFactory queueSqlSessionFactory(
            @Qualifier("dataSource") HikariDataSource dataSource
    ) throws Exception {
        return sqlSessionFactory(dataSource, "classpath*:/mapper/**/*.xml");
    }

    @Bean(name = "sqlSessionTemplate")
    @Primary
    public SqlSessionTemplate queueSqlSessionTemplate(
            @Qualifier("sqlSessionFactory") SqlSessionFactory sqlSessionFactory
    ) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }

    /** 输入: 独立报表数据源; 输出: 仅加载报表查询 SQL 的 MyBatis-Flex 会话工厂. */
    @Bean("reportingSqlSessionFactory")
    public SqlSessionFactory reportingSqlSessionFactory(
            @Qualifier("reportingDataSource") HikariDataSource dataSource
    ) throws Exception {
        return sqlSessionFactory(dataSource, "classpath*:/mapper/*ReportMapper.xml");
    }

    @Bean("reportingSqlSessionTemplate")
    public SqlSessionTemplate reportingSqlSessionTemplate(
            @Qualifier("reportingSqlSessionFactory") SqlSessionFactory sqlSessionFactory
    ) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }

    @Bean
    public RevenueReportMapper revenueReportMapper(
            @Qualifier("reportingSqlSessionTemplate") SqlSessionTemplate sqlSessionTemplate
    ) {
        return sqlSessionTemplate.getMapper(RevenueReportMapper.class);
    }

    @Bean
    public VehicleStatusReportMapper vehicleStatusReportMapper(
            @Qualifier("reportingSqlSessionTemplate") SqlSessionTemplate sqlSessionTemplate
    ) {
        return sqlSessionTemplate.getMapper(VehicleStatusReportMapper.class);
    }

    // 两个数据源必须分别创建会话工厂, 否则 Mapper 可能被错误路由到任务队列连接池.
    private SqlSessionFactory sqlSessionFactory(HikariDataSource dataSource, String mapperLocation) throws Exception {
        var configuration = new FlexConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setArgNameBasedConstructorAutoMapping(true);

        var factory = new FlexSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources(mapperLocation));
        return factory.getObject();
    }

    private HikariConfig dataSourceConfig(
            String poolName,
            String configuredUrl,
            String host,
            int port,
            String database,
            String username,
            String password,
            int poolSize
    ) {
        var jdbcUrl = configuredUrl.isBlank()
                ? "jdbc:postgresql://" + host + ":" + port + "/" + database
                : configuredUrl;
        var config = new HikariConfig();
        config.setPoolName(poolName);
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(poolSize);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(3_000);
        return config;
    }
}

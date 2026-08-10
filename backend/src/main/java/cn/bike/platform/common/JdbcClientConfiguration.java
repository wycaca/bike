package cn.bike.platform.common;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
public class JdbcClientConfiguration {

    /** 输入: 主业务数据源; 输出: 供队列和普通业务仓储使用的主 JdbcClient。 */
    @Bean
    @Primary
    public JdbcClient primaryJdbcClient(@Qualifier("dataSource") DataSource dataSource) {
        return JdbcClient.create(dataSource);
    }

    /** 输入: API 进程的主业务数据源; 输出: 同库交互式报表查询客户端。 */
    @Bean("reportingJdbcClient")
    @Profile("!report-worker")
    public JdbcClient reportingJdbcClient(@Qualifier("dataSource") DataSource dataSource) {
        return JdbcClient.create(dataSource);
    }
}

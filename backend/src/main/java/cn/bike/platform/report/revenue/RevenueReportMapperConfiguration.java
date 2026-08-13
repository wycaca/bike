package cn.bike.platform.report.revenue;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 普通服务进程的收入报表 Mapper 绑定配置.
 * report-worker Profile 使用独立只读会话, 因此在此配置中明确排除.
 */
@Configuration(proxyBeanMethods = false)
@Profile("!report-worker")
public class RevenueReportMapperConfiguration {

    /** 输入: 主业务库会话; 输出: 普通服务进程使用的收入报表 Mapper. */
    @Bean
    public RevenueReportMapper revenueReportMapper(SqlSessionTemplate sqlSessionTemplate) {
        return sqlSessionTemplate.getMapper(RevenueReportMapper.class);
    }
}

package cn.bike.platform.report;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("!report-worker")
public class RevenueReportMapperConfiguration {

    /** 输入: 主业务库会话; 输出: 普通服务进程使用的收入报表 Mapper. */
    @Bean
    public RevenueReportMapper revenueReportMapper(SqlSessionTemplate sqlSessionTemplate) {
        return sqlSessionTemplate.getMapper(RevenueReportMapper.class);
    }
}

package cn.bike.platform.common;

import cn.bike.platform.ops.OperationsMapper;
import cn.bike.platform.report.revenue.RevenueReportMapper;
import com.mybatisflex.core.mybatis.FlexConfiguration;
import com.mybatisflex.spring.FlexSqlSessionFactoryBean;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;
import java.lang.reflect.Modifier;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * MyBatis-Flex Mapper XML 装载契约测试.
 * 使用只提供元数据的 DataSource Mock 构建真实会话工厂, 不依赖运行中的数据库.
 */
class MyBatisMapperTest {

    @Test
    void 应完整加载所有MapperXml() throws Exception {
        var dataSource = mock(DataSource.class);
        var connection = mock(Connection.class);
        var metadata = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getURL()).thenReturn("jdbc:postgresql://localhost/bike");

        var resources = new PathMatchingResourcePatternResolver().getResources("classpath*:/mapper/**/*.xml");
        var factory = new FlexSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(new FlexConfiguration());
        factory.setMapperLocations(resources);

        var configuration = factory.getObject().getConfiguration();

        assertThat(resources).hasSize(7);
        assertThat(configuration.hasStatement("cn.bike.platform.vehicle.VehicleMapper.findVehicles")).isTrue();
        assertThat(configuration.hasStatement("cn.bike.platform.ops.OperationsMapper.findTasks")).isTrue();
        assertThat(configuration.hasStatement("cn.bike.platform.report.revenue.RevenueReportMapper.totals")).isTrue();
        assertThat(configuration.hasStatement("cn.bike.platform.report.export.ReportExportMapper.claimNext")).isTrue();
        assertThat(configuration.hasStatement("cn.bike.platform.report.MockRideOrderMapper.insertRideOrders")).isTrue();
    }

    @Test
    void Mapper代理方法应只暴露公开类型() {
        var inaccessibleTypes = Stream.of(OperationsMapper.class, RevenueReportMapper.class)
                .flatMap(mapper -> Arrays.stream(mapper.getMethods()))
                .flatMap(method -> Stream.concat(
                        Stream.of(method.getReturnType()), Arrays.stream(method.getParameterTypes())))
                .filter(type -> !type.isPrimitive())
                .filter(type -> !Modifier.isPublic(type.getModifiers()))
                .map(Class::getName)
                .distinct()
                .toList();

        assertThat(inaccessibleTypes)
                .as("JDK 动态代理无法访问 Mapper 签名中的包级类型")
                .isEmpty();
    }
}

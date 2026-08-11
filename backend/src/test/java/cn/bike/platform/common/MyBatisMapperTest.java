package cn.bike.platform.common;

import com.mybatisflex.core.mybatis.FlexConfiguration;
import com.mybatisflex.spring.FlexSqlSessionFactoryBean;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

        assertThat(resources).hasSize(5);
        assertThat(configuration.hasStatement("cn.bike.platform.vehicle.VehicleMapper.findVehicles")).isTrue();
        assertThat(configuration.hasStatement("cn.bike.platform.ops.OperationsMapper.findTasks")).isTrue();
        assertThat(configuration.hasStatement("cn.bike.platform.report.RevenueReportMapper.totals")).isTrue();
        assertThat(configuration.hasStatement("cn.bike.platform.report.ReportExportMapper.claimNext")).isTrue();
        assertThat(configuration.hasStatement("cn.bike.platform.report.MockRideOrderMapper.insertRideOrders")).isTrue();
    }
}

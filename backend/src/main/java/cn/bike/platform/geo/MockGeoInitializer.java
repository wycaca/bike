package cn.bike.platform.geo;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Profile("mock")
@Component
@Order(20)
public class MockGeoInitializer implements ApplicationRunner {

    private final JdbcClient jdbcClient;

    public MockGeoInitializer(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /** 输入: Spring Boot 启动参数; 输出: 无, 幂等创建两城空间设施样例。 */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        insertFence("FNC-BJ-OP", "ORG-BJ", "北京试点运营区", "110000", "OPERATION",
                "POLYGON((116.15 39.78,116.56 39.78,116.56 40.04,116.15 40.04,116.15 39.78))");
        insertFence("FNC-BJ-NP", "ORG-BJ", "东城禁停示范区", "110000", "NO_PARK",
                "POLYGON((116.405 39.915,116.430 39.915,116.430 39.935,116.405 39.935,116.405 39.915))");
        insertFence("FNC-SH-OP", "ORG-SH", "上海试点运营区", "310000", "OPERATION",
                "POLYGON((121.35 31.14,121.64 31.14,121.64 31.34,121.35 31.34,121.35 31.14))");
        insertParkingPoint("PRK-BJ-001", "ORG-BJ", "东城示范停车点", "110000", 116.418, 39.926);
        insertParkingPoint("PRK-BJ-002", "ORG-BJ", "海淀示范停车点", "110000", 116.314, 39.974);
        insertParkingPoint("PRK-SH-001", "ORG-SH", "黄浦示范停车点", "310000", 121.484, 31.231);
        insertParkingPoint("PRK-SH-002", "ORG-SH", "杨浦示范停车点", "310000", 121.522, 31.296);
    }

    private void insertFence(String id, String orgId, String name, String cityCode, String type, String wkt) {
        jdbcClient.sql("""
                        INSERT INTO geofence (
                            fence_id, org_id, fence_name, city_code, fence_type, boundary, status, created_by
                        ) VALUES (:id, :orgId, :name, :cityCode, :type, ST_GeomFromText(:wkt, 4326), 'ACTIVE', 'USR-ADMIN')
                        ON CONFLICT (fence_id) DO NOTHING
                        """)
                .param("id", id).param("orgId", orgId).param("name", name)
                .param("cityCode", cityCode).param("type", type).param("wkt", wkt).update();
    }

    private void insertParkingPoint(
            String id, String orgId, String name, String cityCode, double longitude, double latitude
    ) {
        jdbcClient.sql("""
                        INSERT INTO parking_point (
                            point_id, org_id, point_name, city_code, location,
                            radius_meters, capacity, status, created_by
                        ) VALUES (
                            :id, :orgId, :name, :cityCode,
                            ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326),
                            300, 80, 'ACTIVE', 'USR-ADMIN'
                        ) ON CONFLICT (point_id) DO NOTHING
                        """)
                .param("id", id).param("orgId", orgId).param("name", name).param("cityCode", cityCode)
                .param("longitude", longitude).param("latitude", latitude).update();
    }
}

package cn.bike.platform.geo;

import cn.bike.platform.admin.AdminModels.UserRole;
import cn.bike.platform.geo.GeoModels.Coordinate;
import cn.bike.platform.geo.GeoModels.FacilityStatus;
import cn.bike.platform.geo.GeoModels.ParkingPointRequest;
import cn.bike.platform.security.PlatformPrincipal;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class GeoServiceTest {

    @Test
    void 应生成PostGis可识别的闭合多边形() {
        var boundary = List.of(
                coordinate("116.10", "39.80"),
                coordinate("116.20", "39.80"),
                coordinate("116.20", "39.90"),
                coordinate("116.10", "39.80")
        );

        GeoService.validateBoundary(boundary);

        assertThat(GeoService.polygonWkt(boundary))
                .isEqualTo("POLYGON((116.10 39.80,116.20 39.80,116.20 39.90,116.10 39.80))");
    }

    @Test
    void 应拒绝首尾不闭合的围栏() {
        var boundary = List.of(
                coordinate("116.10", "39.80"),
                coordinate("116.20", "39.80"),
                coordinate("116.20", "39.90"),
                coordinate("116.10", "39.90")
        );

        assertThatThrownBy(() -> GeoService.validateBoundary(boundary))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("首尾坐标必须闭合");
    }

    @Test
    void 应拒绝只有两个不同顶点的退化围栏() {
        var boundary = List.of(
                coordinate("116.10", "39.80"),
                coordinate("116.20", "39.80"),
                coordinate("116.10", "39.80"),
                coordinate("116.10", "39.80")
        );

        assertThatThrownBy(() -> GeoService.validateBoundary(boundary))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("3 个不同顶点");
    }

    @Test
    void 应拒绝把设施挂到不负责当前城市的组织() {
        var repository = mock(GeoRepository.class);
        var service = new GeoService(repository);
        var request = new ParkingPointRequest(
                "北京停车点", "110000", "ORG-SH", FacilityStatus.ACTIVE,
                coordinate("116.40", "39.90"), new BigDecimal("300"), 80
        );
        var operator = new PlatformPrincipal(
                "USR-ADMIN", "admin", "encoded", "系统管理员",
                "ORG-HQ", "运营总部", UserRole.ADMIN, true
        );
        when(repository.organizationSupportsCity("ORG-SH", "110000")).thenReturn(false);

        assertThatThrownBy(() -> service.createParkingPoint(request, operator))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不负责当前城市");
        verify(repository).organizationSupportsCity("ORG-SH", "110000");
        verifyNoMoreInteractions(repository);
    }

    private Coordinate coordinate(String longitude, String latitude) {
        return new Coordinate(new BigDecimal(longitude), new BigDecimal(latitude));
    }
}

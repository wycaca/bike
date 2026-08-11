package cn.bike.platform.vehicle;

import cn.bike.platform.admin.AdminModels.UserRole;
import cn.bike.platform.security.PlatformAccessPolicy;
import cn.bike.platform.security.PlatformPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class VehicleServiceTest {

    @Test
    void 缩放级别越高聚合网格越小() {
        assertThat(VehicleService.gridSizeForZoom(15))
                .isLessThan(VehicleService.gridSizeForZoom(10));
    }

    @Test
    void 运维人员查询车辆时应强制使用所属城市() {
        var repository = mock(VehicleRepository.class);
        var service = new VehicleService(repository, mock(LatestVehicleCache.class), new PlatformAccessPolicy());

        service.findVehicles(1, 20, null, null, null, operator());

        verify(repository).findVehicles(1, 20, null, "110000", null);
        assertThatThrownBy(() -> service.findVehicles(1, 20, null, "310000", null, operator()))
                .isInstanceOf(AccessDeniedException.class);
    }

    private PlatformPrincipal operator() {
        return new PlatformPrincipal("USR-OP", "operator.bj", "encoded", "北京运维",
                "ORG-BJ", "北京运营中心", "110000", UserRole.OPERATOR, true);
    }
}

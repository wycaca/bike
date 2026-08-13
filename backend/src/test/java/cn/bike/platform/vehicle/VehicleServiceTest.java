package cn.bike.platform.vehicle;

import cn.bike.platform.admin.AdminModels.DataScope;
import cn.bike.platform.security.DataPermission;
import cn.bike.platform.security.DataPermissionService;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VehicleServiceTest {

    @Test
    void 缩放级别越高聚合网格越小() {
        assertThat(VehicleService.gridSizeForZoom(15))
                .isLessThan(VehicleService.gridSizeForZoom(10));
    }

    @Test
    void 查询车辆时应把登录人的组织权限传给仓储() {
        var repository = mock(VehicleRepository.class);
        var permissions = mock(DataPermissionService.class);
        var permission = new DataPermission(DataScope.ORG_ONLY, "ORG-BJ", List.of("ORG-BJ"));
        var principal = mock(cn.bike.platform.security.PlatformPrincipal.class);
        when(permissions.resolve(principal)).thenReturn(permission);
        var service = new VehicleService(repository, mock(LatestVehicleCache.class), permissions);

        service.findVehicles(1, 20, null, null, null, principal);

        verify(repository).findVehicles(1, 20, null, null, null, permission);
    }
}

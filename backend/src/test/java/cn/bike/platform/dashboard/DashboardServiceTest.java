package cn.bike.platform.dashboard;

import cn.bike.platform.TestDataPermissions;
import cn.bike.platform.admin.AdminModels.DataScope;
import cn.bike.platform.admin.AdminModels.UserRole;
import cn.bike.platform.security.PlatformPrincipal;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;

class DashboardServiceTest {

    @Test
    void 趋势天数超出范围时应拒绝请求() {
        var service = new DashboardService(mock(DashboardRepository.class), TestDataPermissions.allService());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.dashboard("110000", 32, principal()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 到 31");
    }

    private PlatformPrincipal principal() {
        return new PlatformPrincipal("USR-ADMIN", "admin", "encoded", "系统管理员",
                "ORG-HQ", "运营总部", UserRole.ADMIN, DataScope.ALL, true);
    }
}

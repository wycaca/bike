package cn.bike.platform.city;

import cn.bike.platform.admin.AdminModels.DataScope;
import cn.bike.platform.admin.AdminModels.Organization;
import cn.bike.platform.admin.AdminModels.OrganizationType;
import cn.bike.platform.admin.AdminModels.RecordStatus;
import cn.bike.platform.admin.AdminModels.UserRole;
import cn.bike.platform.admin.AdminRepository;
import cn.bike.platform.city.CityModels.CityRequest;
import cn.bike.platform.security.DataPermissionService;
import cn.bike.platform.security.PlatformPrincipal;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CityServiceTest {

    @Test
    void 城市中心点必须位于地图边界内() {
        var repository = mock(CityRepository.class);
        var adminRepository = mock(AdminRepository.class);
        when(adminRepository.findOrganization("ORG-GZ")).thenReturn(Optional.of(new Organization(
                "ORG-GZ", "ORG-HQ", "广州中心", OrganizationType.REGION, "440100", RecordStatus.ACTIVE,
                Instant.now(), Instant.now())));
        var service = new CityService(repository, adminRepository, mock(DataPermissionService.class));
        var request = new CityRequest("440100", "广州", "ORG-GZ",
                value("114"), value("23"), value("112"), value("22"), value("113"), value("24"),
                RecordStatus.ACTIVE);

        assertThrows(IllegalArgumentException.class, () -> service.create(request, globalAdmin()));
    }

    private BigDecimal value(String value) {
        return new BigDecimal(value);
    }

    private PlatformPrincipal globalAdmin() {
        return new PlatformPrincipal("USR-1", "admin", "hash", "管理员", "ORG-HQ", "总部",
                UserRole.ADMIN, DataScope.ALL, true);
    }
}

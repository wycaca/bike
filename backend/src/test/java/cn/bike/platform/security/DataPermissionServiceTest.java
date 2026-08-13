package cn.bike.platform.security;

import cn.bike.platform.admin.AdminModels.DataScope;
import cn.bike.platform.admin.AdminRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DataPermissionServiceTest {

    @Test
    void 本组织及下级应使用递归组织集合() {
        var repository = mock(AdminRepository.class);
        when(repository.findOrganizationTreeIds("ORG-BJ"))
                .thenReturn(List.of("ORG-BJ", "ORG-BJ-TEAM-1"));

        var permission = new DataPermissionService(repository)
                .resolve(DataScope.ORG_AND_CHILDREN, "ORG-BJ");

        assertThat(permission.orgIds()).containsExactly("ORG-BJ", "ORG-BJ-TEAM-1");
        assertThat(permission.includes("ORG-SH")).isFalse();
    }

    @Test
    void 目标组织超出范围时应拒绝写操作() {
        var service = new DataPermissionService(mock(AdminRepository.class));
        var permission = new DataPermission(DataScope.ORG_ONLY, "ORG-BJ", List.of("ORG-BJ"));

        assertThatThrownBy(() -> service.requireOrganization(permission, "ORG-SH"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("数据权限");
    }
}

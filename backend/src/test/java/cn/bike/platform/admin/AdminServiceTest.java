package cn.bike.platform.admin;

import cn.bike.platform.admin.AdminModels.RecordStatus;
import cn.bike.platform.admin.AdminModels.UserRequest;
import cn.bike.platform.admin.AdminModels.UserRole;
import cn.bike.platform.security.PlatformPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

import java.util.Optional;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminServiceTest {

    @Test
    void 管理员不能停用当前登录账号() {
        var repository = mock(AdminRepository.class);
        var passwordEncoder = mock(PasswordEncoder.class);
        var service = new AdminService(repository, passwordEncoder, sessionRepository());
        var request = new UserRequest(
                "admin", "系统管理员", "13800000000", "ORG-HQ",
                UserRole.ADMIN, RecordStatus.DISABLED, null
        );
        var principal = new PlatformPrincipal(
                "USR-ADMIN", "admin", "encoded", "系统管理员", "ORG-HQ", "运营总部",
                UserRole.ADMIN, true
        );
        when(repository.findOrganization("ORG-HQ"))
                .thenReturn(Optional.of(new AdminModels.Organization(
                        "ORG-HQ", null, "运营总部", AdminModels.OrganizationType.COMPANY,
                        null, RecordStatus.ACTIVE, null, null
                )));

        assertThatThrownBy(() -> service.updateUser("USR-ADMIN", request, principal))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能停用当前登录账号");

        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void 组织不能把自己的下级设为上级() {
        var repository = mock(AdminRepository.class);
        var service = new AdminService(repository, mock(PasswordEncoder.class), sessionRepository());
        var request = new AdminModels.OrganizationRequest(
                "ORG-TEAM", "运营总部", AdminModels.OrganizationType.COMPANY,
                "", RecordStatus.ACTIVE
        );
        when(repository.findOrganization("ORG-TEAM")).thenReturn(Optional.of(new AdminModels.Organization(
                "ORG-TEAM", "ORG-BJ", "东城班组", AdminModels.OrganizationType.TEAM,
                "110000", RecordStatus.ACTIVE, null, null
        )));
        when(repository.findOrganization("ORG-BJ")).thenReturn(Optional.of(new AdminModels.Organization(
                "ORG-BJ", "ORG-HQ", "北京运营中心", AdminModels.OrganizationType.REGION,
                "110000", RecordStatus.ACTIVE, null, null
        )));

        assertThatThrownBy(() -> service.updateOrganization("ORG-HQ", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能是当前组织的下级");
    }

    @Test
    @SuppressWarnings("unchecked")
    void 重置密码应撤销用户的全部现有会话() {
        var repository = mock(AdminRepository.class);
        var passwordEncoder = mock(PasswordEncoder.class);
        var sessions = (FindByIndexNameSessionRepository<Session>) mock(FindByIndexNameSessionRepository.class);
        var service = new AdminService(repository, passwordEncoder, sessions);
        var user = new AdminModels.PlatformUser("USR-OP", "operator.bj", "北京运维", "13800000000",
                "ORG-BJ", "北京运营中心", UserRole.OPERATOR, RecordStatus.ACTIVE, null, null);
        when(repository.findUser("USR-OP")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("NewPassword123!")).thenReturn("new-encoded");
        when(repository.updatePassword("USR-OP", "new-encoded")).thenReturn(1);
        when(sessions.findByPrincipalName("operator.bj")).thenReturn(Map.of("SESSION-1", mock(Session.class)));

        service.resetPassword("USR-OP", new AdminModels.PasswordResetRequest("NewPassword123!"));

        verify(sessions).deleteById("SESSION-1");
    }

    @SuppressWarnings("unchecked")
    private static FindByIndexNameSessionRepository<? extends Session> sessionRepository() {
        return mock(FindByIndexNameSessionRepository.class);
    }
}

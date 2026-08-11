package cn.bike.platform.security;

import cn.bike.platform.admin.AdminModels.UserRole;
import cn.bike.platform.ops.OperationsModels.TaskItem;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlatformAccessPolicyTest {

    private final PlatformAccessPolicy policy = new PlatformAccessPolicy();

    @Test
    void 运维人员不能读取其他城市数据() {
        assertThatThrownBy(() -> policy.requireCity(operator(), "310000"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("所属城市");
    }

    @Test
    void 运维人员不能读取同城其他组织任务() {
        var task = mock(TaskItem.class);
        when(task.cityCode()).thenReturn("110000");
        when(task.orgId()).thenReturn("ORG-BJ-OTHER");

        assertThatThrownBy(() -> policy.requireTask(operator(), task))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("所属组织");
    }

    private PlatformPrincipal operator() {
        return new PlatformPrincipal("USR-OP", "operator.bj", "encoded", "北京运维",
                "ORG-BJ", "北京运营中心", "110000", UserRole.OPERATOR, true);
    }
}

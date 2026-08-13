package cn.bike.platform;

import cn.bike.platform.admin.AdminModels.DataScope;
import cn.bike.platform.security.DataPermission;
import cn.bike.platform.security.DataPermissionService;
import cn.bike.platform.security.PlatformPrincipal;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 测试数据权限工厂, 为既有业务测试提供不限制组织的统一上下文. */
public final class TestDataPermissions {

    public static final DataPermission ALL = new DataPermission(DataScope.ALL, null, List.of());

    private TestDataPermissions() {
    }

    /** 输入: 无; 输出: 所有解析均返回全量范围的权限服务 Mock. */
    public static DataPermissionService allService() {
        var service = mock(DataPermissionService.class);
        when(service.resolve(any(PlatformPrincipal.class))).thenReturn(ALL);
        when(service.resolve(any(DataScope.class), nullable(String.class))).thenReturn(ALL);
        return service;
    }
}

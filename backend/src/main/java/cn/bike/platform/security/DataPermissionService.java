package cn.bike.platform.security;

import cn.bike.platform.admin.AdminModels.DataScope;
import cn.bike.platform.admin.AdminRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;

/** 解析并校验用户的数据权限范围. */
@Service
public class DataPermissionService {

    private final AdminRepository repository;

    public DataPermissionService(AdminRepository repository) {
        this.repository = repository;
    }

    /** 输入: 当前登录用户; 输出: 本次请求允许访问的组织集合. */
    public DataPermission resolve(PlatformPrincipal principal) {
        return resolve(principal.dataScope(), principal.orgId());
    }

    /** 输入: 数据范围和根组织; 输出: 可供异步任务复用的权限快照. */
    public DataPermission resolve(DataScope dataScope, String orgId) {
        var orgIds = switch (dataScope) {
            case ALL -> List.<String>of();
            case ORG_ONLY -> List.of(orgId);
            case ORG_AND_CHILDREN -> repository.findOrganizationTreeIds(orgId);
        };
        return new DataPermission(dataScope, orgId, List.copyOf(orgIds));
    }

    /** 输入: 权限快照和目标组织; 输出: 无, 越权时拒绝请求. */
    public void requireOrganization(DataPermission permission, String orgId) {
        if (!permission.includes(orgId)) {
            throw new AccessDeniedException("没有目标组织的数据权限");
        }
    }
}

package cn.bike.platform.security;

import cn.bike.platform.admin.AdminModels.DataScope;

import java.util.List;

/**
 * 当前请求的数据权限快照.
 * 全部范围不展开组织树, 其余范围保存允许访问的组织编号, 供数据库查询和写操作统一校验.
 */
public record DataPermission(DataScope dataScope, String rootOrgId, List<String> orgIds) {

    /** 输入: 目标组织; 输出: 当前范围是否允许访问. */
    public boolean includes(String orgId) {
        return dataScope == DataScope.ALL || orgIds.contains(orgId);
    }

    /** 输入: 无; 输出: 是否不需要附加组织过滤. */
    public boolean unrestricted() {
        return dataScope == DataScope.ALL;
    }
}

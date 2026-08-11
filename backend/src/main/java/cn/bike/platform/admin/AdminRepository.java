package cn.bike.platform.admin;

import cn.bike.platform.admin.AdminModels.AuditLog;
import cn.bike.platform.admin.AdminModels.Organization;
import cn.bike.platform.admin.AdminModels.OrganizationRequest;
import cn.bike.platform.admin.AdminModels.PlatformUser;
import cn.bike.platform.admin.AdminModels.RecordStatus;
import cn.bike.platform.admin.AdminModels.UserRequest;
import cn.bike.platform.admin.AdminModels.UserRole;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 管理域仓储, 在服务层和 MyBatis Mapper 之间转换查询条件与领域对象.
 * 用户名去重和状态约束依赖数据库约束及 Mapper SQL, 避免多实例下的内存判断竞态.
 */
@Repository
public class AdminRepository {

    private final AdminMapper mapper;

    public AdminRepository(AdminMapper mapper) {
        this.mapper = mapper;
    }

    /** 输入: 无; 输出: 按组织名称排序的全部组织。 */
    public List<Organization> findOrganizations() {
        return mapper.findOrganizations();
    }

    /** 输入: 组织编号; 输出: 对应组织, 不存在时为空。 */
    public Optional<Organization> findOrganization(String orgId) {
        return Optional.ofNullable(mapper.findOrganization(orgId));
    }

    /** 输入: 组织编号与请求; 输出: 无, 新增组织记录。 */
    public void insertOrganization(String orgId, OrganizationRequest request) {
        mapper.insertOrganization(orgId, request.parentOrgId(), request.orgName().trim(), request.orgType().name(),
                blankToNull(request.cityCode()), request.status().name());
    }

    /** 输入: 组织编号与请求; 输出: 受影响行数。 */
    public int updateOrganization(String orgId, OrganizationRequest request) {
        return mapper.updateOrganization(orgId, request.parentOrgId(), request.orgName().trim(),
                request.orgType().name(), blankToNull(request.cityCode()), request.status().name());
    }

    /** 输入: 用户名; 输出: 含密码摘要的认证用户。 */
    public Optional<AuthenticatedUser> findAuthenticatedUser(String username) {
        return Optional.ofNullable(mapper.findAuthenticatedUser(username))
                .map(row -> new AuthenticatedUser(
                        row.userId(), row.username(), row.passwordHash(), row.displayName(),
                        row.orgId(), row.orgName(), row.role(), row.status()));
    }

    /** 输入: 分页和关键字; 输出: 用户列表。 */
    public List<PlatformUser> findUsers(int page, int pageSize, String keyword) {
        return mapper.findUsers(fuzzy(keyword), pageSize, (page - 1) * pageSize);
    }

    /** 输入: 关键字; 输出: 匹配用户总数。 */
    public long countUsers(String keyword) {
        return mapper.countUsers(fuzzy(keyword));
    }

    /** 输入: 用户编号; 输出: 用户详情。 */
    public Optional<PlatformUser> findUser(String userId) {
        return Optional.ofNullable(mapper.findUser(userId));
    }

    /** 输入: 用户编号、请求和密码摘要; 输出: 无, 新增用户。 */
    public void insertUser(String userId, UserRequest request, String passwordHash) {
        mapper.insertUser(userId, request.username().trim(), passwordHash, request.displayName().trim(),
                blankToNull(request.phone()), request.orgId(), request.role().name(), request.status().name());
    }

    /** 输入: 用户编号与请求; 输出: 受影响行数。 */
    public int updateUser(String userId, UserRequest request) {
        return mapper.updateUser(userId, request.username().trim(), request.displayName().trim(),
                blankToNull(request.phone()), request.orgId(), request.role().name(), request.status().name());
    }

    /** 输入: 用户编号和密码摘要; 输出: 受影响行数。 */
    public int updatePassword(String userId, String passwordHash) {
        return mapper.updatePassword(userId, passwordHash);
    }

    /** 输入: 用户名; 输出: 无, 更新最后登录时间。 */
    public void updateLastLogin(String username) {
        mapper.updateLastLogin(username);
    }

    /** 输入: 审计查询条件; 输出: 审计日志列表。 */
    public List<AuditLog> findAuditLogs(int page, int pageSize, String keyword, String action) {
        return mapper.findAuditLogs(fuzzy(keyword), exactOrAny(action), pageSize, (page - 1) * pageSize);
    }

    /** 输入: 审计查询条件; 输出: 匹配日志总数。 */
    public long countAuditLogs(String keyword, String action) {
        return mapper.countAuditLogs(fuzzy(keyword), exactOrAny(action));
    }

    /** 输入: 完整审计记录字段; 输出: 无, 写入一条审计日志。 */
    public void insertAudit(
            String userId, String username, String orgId, String action, String resourceType,
            String resourceId, String method, String path, String ip, int statusCode, long durationMs,
            String detail
    ) {
        mapper.insertAudit(userId, username, orgId, action, resourceType, resourceId, method, path, ip,
                statusCode, durationMs, detail);
    }

    private String fuzzy(String value) {
        return value == null || value.isBlank() ? "%" : "%" + value.trim() + "%";
    }

    private String exactOrAny(String value) {
        return value == null || value.isBlank() ? "%" : value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record AuthenticatedUser(
            String userId, String username, String passwordHash, String displayName,
            String orgId, String orgName, UserRole role, RecordStatus status
    ) {
    }
}

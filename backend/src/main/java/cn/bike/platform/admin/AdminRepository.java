package cn.bike.platform.admin;

import cn.bike.platform.admin.AdminModels.AuditLog;
import cn.bike.platform.admin.AdminModels.Organization;
import cn.bike.platform.admin.AdminModels.OrganizationRequest;
import cn.bike.platform.admin.AdminModels.OrganizationType;
import cn.bike.platform.admin.AdminModels.PlatformUser;
import cn.bike.platform.admin.AdminModels.RecordStatus;
import cn.bike.platform.admin.AdminModels.UserRequest;
import cn.bike.platform.admin.AdminModels.UserRole;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class AdminRepository {

    private final JdbcClient jdbcClient;

    public AdminRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /** 输入: 无; 输出: 按组织名称排序的全部组织。 */
    public List<Organization> findOrganizations() {
        return jdbcClient.sql("SELECT * FROM organization ORDER BY org_type, org_name")
                .query(this::mapOrganization).list();
    }

    /** 输入: 组织编号; 输出: 对应组织, 不存在时为空。 */
    public Optional<Organization> findOrganization(String orgId) {
        return jdbcClient.sql("SELECT * FROM organization WHERE org_id = :orgId")
                .param("orgId", orgId).query(this::mapOrganization).optional();
    }

    /** 输入: 组织编号与请求; 输出: 无, 新增组织记录。 */
    public void insertOrganization(String orgId, OrganizationRequest request) {
        jdbcClient.sql("""
                        INSERT INTO organization (
                            org_id, parent_org_id, org_name, org_type, city_code, status
                        ) VALUES (:orgId, :parentOrgId, :orgName, :orgType, :cityCode, :status)
                        """)
                .param("orgId", orgId)
                .param("parentOrgId", request.parentOrgId())
                .param("orgName", request.orgName().trim())
                .param("orgType", request.orgType().name())
                .param("cityCode", blankToNull(request.cityCode()))
                .param("status", request.status().name())
                .update();
    }

    /** 输入: 组织编号与请求; 输出: 受影响行数。 */
    public int updateOrganization(String orgId, OrganizationRequest request) {
        return jdbcClient.sql("""
                        UPDATE organization SET parent_org_id = :parentOrgId, org_name = :orgName,
                            org_type = :orgType, city_code = :cityCode, status = :status, updated_at = now()
                        WHERE org_id = :orgId
                        """)
                .param("orgId", orgId)
                .param("parentOrgId", request.parentOrgId())
                .param("orgName", request.orgName().trim())
                .param("orgType", request.orgType().name())
                .param("cityCode", blankToNull(request.cityCode()))
                .param("status", request.status().name())
                .update();
    }

    /** 输入: 用户名; 输出: 含密码摘要的认证用户。 */
    public Optional<AuthenticatedUser> findAuthenticatedUser(String username) {
        return jdbcClient.sql("""
                        SELECT u.user_id, u.username, u.password_hash, u.display_name, u.org_id,
                               o.org_name, u.role, u.status
                        FROM app_user u JOIN organization o ON o.org_id = u.org_id
                        WHERE lower(u.username) = lower(:username) AND o.status = 'ACTIVE'
                        """)
                .param("username", username)
                .query((rs, rowNum) -> new AuthenticatedUser(
                        rs.getString("user_id"), rs.getString("username"), rs.getString("password_hash"),
                        rs.getString("display_name"), rs.getString("org_id"), rs.getString("org_name"),
                        UserRole.valueOf(rs.getString("role")), RecordStatus.valueOf(rs.getString("status"))))
                .optional();
    }

    /** 输入: 分页和关键字; 输出: 用户列表。 */
    public List<PlatformUser> findUsers(int page, int pageSize, String keyword) {
        var filter = keyword == null || keyword.isBlank() ? "%" : "%" + keyword.trim() + "%";
        return jdbcClient.sql("""
                        SELECT u.*, o.org_name FROM app_user u
                        JOIN organization o ON o.org_id = u.org_id
                        WHERE u.username ILIKE :filter OR u.display_name ILIKE :filter OR u.phone ILIKE :filter
                        ORDER BY u.created_at DESC LIMIT :limit OFFSET :offset
                        """)
                .param("filter", filter).param("limit", pageSize).param("offset", (page - 1) * pageSize)
                .query(this::mapUser).list();
    }

    /** 输入: 关键字; 输出: 匹配用户总数。 */
    public long countUsers(String keyword) {
        var filter = keyword == null || keyword.isBlank() ? "%" : "%" + keyword.trim() + "%";
        return jdbcClient.sql("""
                        SELECT count(*) FROM app_user
                        WHERE username ILIKE :filter OR display_name ILIKE :filter OR phone ILIKE :filter
                        """)
                .param("filter", filter).query(Long.class).single();
    }

    /** 输入: 用户编号; 输出: 用户详情。 */
    public Optional<PlatformUser> findUser(String userId) {
        return jdbcClient.sql("""
                        SELECT u.*, o.org_name FROM app_user u
                        JOIN organization o ON o.org_id = u.org_id WHERE u.user_id = :userId
                        """)
                .param("userId", userId).query(this::mapUser).optional();
    }

    /** 输入: 用户编号、请求和密码摘要; 输出: 无, 新增用户。 */
    public void insertUser(String userId, UserRequest request, String passwordHash) {
        jdbcClient.sql("""
                        INSERT INTO app_user (
                            user_id, username, password_hash, display_name, phone, org_id, role, status
                        ) VALUES (
                            :userId, :username, :passwordHash, :displayName, :phone, :orgId, :role, :status
                        )
                        """)
                .param("userId", userId).param("username", request.username().trim())
                .param("passwordHash", passwordHash).param("displayName", request.displayName().trim())
                .param("phone", blankToNull(request.phone())).param("orgId", request.orgId())
                .param("role", request.role().name()).param("status", request.status().name()).update();
    }

    /** 输入: 用户编号与请求; 输出: 受影响行数。 */
    public int updateUser(String userId, UserRequest request) {
        return jdbcClient.sql("""
                        UPDATE app_user SET username = :username, display_name = :displayName,
                            phone = :phone, org_id = :orgId, role = :role, status = :status, updated_at = now()
                        WHERE user_id = :userId
                        """)
                .param("userId", userId).param("username", request.username().trim())
                .param("displayName", request.displayName().trim()).param("phone", blankToNull(request.phone()))
                .param("orgId", request.orgId()).param("role", request.role().name())
                .param("status", request.status().name()).update();
    }

    /** 输入: 用户编号和密码摘要; 输出: 受影响行数。 */
    public int updatePassword(String userId, String passwordHash) {
        return jdbcClient.sql("UPDATE app_user SET password_hash = :hash, updated_at = now() WHERE user_id = :userId")
                .param("hash", passwordHash).param("userId", userId).update();
    }

    /** 输入: 用户名; 输出: 无, 更新最后登录时间。 */
    public void updateLastLogin(String username) {
        jdbcClient.sql("UPDATE app_user SET last_login_at = now() WHERE username = :username")
                .param("username", username).update();
    }

    /** 输入: 审计查询条件; 输出: 审计日志列表。 */
    public List<AuditLog> findAuditLogs(int page, int pageSize, String keyword, String action) {
        var filter = keyword == null || keyword.isBlank() ? "%" : "%" + keyword.trim() + "%";
        var actionFilter = action == null || action.isBlank() ? "%" : action.trim();
        return jdbcClient.sql("""
                        SELECT * FROM audit_log
                        WHERE (username ILIKE :filter OR resource_type ILIKE :filter OR request_path ILIKE :filter)
                          AND action ILIKE :action
                        ORDER BY created_at DESC LIMIT :limit OFFSET :offset
                        """)
                .param("filter", filter).param("action", actionFilter)
                .param("limit", pageSize).param("offset", (page - 1) * pageSize)
                .query(this::mapAudit).list();
    }

    /** 输入: 审计查询条件; 输出: 匹配日志总数。 */
    public long countAuditLogs(String keyword, String action) {
        var filter = keyword == null || keyword.isBlank() ? "%" : "%" + keyword.trim() + "%";
        var actionFilter = action == null || action.isBlank() ? "%" : action.trim();
        return jdbcClient.sql("""
                        SELECT count(*) FROM audit_log
                        WHERE (username ILIKE :filter OR resource_type ILIKE :filter OR request_path ILIKE :filter)
                          AND action ILIKE :action
                        """)
                .param("filter", filter).param("action", actionFilter).query(Long.class).single();
    }

    /** 输入: 完整审计记录字段; 输出: 无, 写入一条审计日志。 */
    public void insertAudit(
            String userId, String username, String orgId, String action, String resourceType,
            String resourceId, String method, String path, String ip, int statusCode, long durationMs,
            String detail
    ) {
        jdbcClient.sql("""
                        INSERT INTO audit_log (
                            user_id, username, org_id, action, resource_type, resource_id,
                            request_method, request_path, client_ip, status_code, duration_ms, detail
                        ) VALUES (
                            :userId, :username, :orgId, :action, :resourceType, :resourceId,
                            :method, :path, :ip, :statusCode, :durationMs, CAST(:detail AS jsonb)
                        )
                        """)
                .param("userId", userId).param("username", username).param("orgId", orgId)
                .param("action", action).param("resourceType", resourceType).param("resourceId", resourceId)
                .param("method", method).param("path", path).param("ip", ip)
                .param("statusCode", statusCode).param("durationMs", durationMs).param("detail", detail).update();
    }

    private Organization mapOrganization(ResultSet rs, int rowNum) throws SQLException {
        return new Organization(rs.getString("org_id"), rs.getString("parent_org_id"), rs.getString("org_name"),
                OrganizationType.valueOf(rs.getString("org_type")), rs.getString("city_code"),
                RecordStatus.valueOf(rs.getString("status")), instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    private PlatformUser mapUser(ResultSet rs, int rowNum) throws SQLException {
        return new PlatformUser(rs.getString("user_id"), rs.getString("username"), rs.getString("display_name"),
                rs.getString("phone"), rs.getString("org_id"), rs.getString("org_name"),
                UserRole.valueOf(rs.getString("role")), RecordStatus.valueOf(rs.getString("status")),
                instant(rs, "last_login_at"), instant(rs, "created_at"));
    }

    private AuditLog mapAudit(ResultSet rs, int rowNum) throws SQLException {
        return new AuditLog(rs.getLong("audit_id"), rs.getString("user_id"), rs.getString("username"),
                rs.getString("org_id"), rs.getString("action"), rs.getString("resource_type"),
                rs.getString("resource_id"), rs.getString("request_method"), rs.getString("request_path"),
                rs.getString("client_ip"), rs.getInt("status_code"), rs.getLong("duration_ms"),
                rs.getString("detail"), instant(rs, "created_at"));
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
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

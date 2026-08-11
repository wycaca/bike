package cn.bike.platform.admin;

import cn.bike.platform.admin.AdminModels.AuditLog;
import cn.bike.platform.admin.AdminModels.Organization;
import cn.bike.platform.admin.AdminModels.PlatformUser;
import cn.bike.platform.admin.AdminModels.RecordStatus;
import cn.bike.platform.admin.AdminModels.UserRole;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 管理域持久化 Mapper, 负责组织、用户和审计日志 SQL.
 * SQL 返回数据库行模型, 查询条件整理和领域转换由 Repository 负责.
 */
@Mapper
public interface AdminMapper {

    @Select("SELECT * FROM organization ORDER BY org_type, org_name")
    List<Organization> findOrganizations();

    @Select("SELECT * FROM organization WHERE org_id = #{orgId}")
    Organization findOrganization(@Param("orgId") String orgId);

    @Insert("""
            INSERT INTO organization (
                org_id, parent_org_id, org_name, org_type, city_code, status
            ) VALUES (#{orgId}, #{parentOrgId}, #{orgName}, #{orgType}, #{cityCode}, #{status})
            """)
    int insertOrganization(
            @Param("orgId") String orgId,
            @Param("parentOrgId") String parentOrgId,
            @Param("orgName") String orgName,
            @Param("orgType") String orgType,
            @Param("cityCode") String cityCode,
            @Param("status") String status
    );

    @Update("""
            UPDATE organization SET parent_org_id = #{parentOrgId}, org_name = #{orgName},
                org_type = #{orgType}, city_code = #{cityCode}, status = #{status}, updated_at = now()
            WHERE org_id = #{orgId}
            """)
    int updateOrganization(
            @Param("orgId") String orgId,
            @Param("parentOrgId") String parentOrgId,
            @Param("orgName") String orgName,
            @Param("orgType") String orgType,
            @Param("cityCode") String cityCode,
            @Param("status") String status
    );

    @Select("""
            SELECT u.user_id, u.username, u.password_hash, u.display_name, u.org_id,
                   o.org_name, u.role, u.status
            FROM app_user u JOIN organization o ON o.org_id = u.org_id
            WHERE lower(u.username) = lower(#{username}) AND o.status = 'ACTIVE'
            """)
    AuthenticatedUserRow findAuthenticatedUser(@Param("username") String username);

    @Select("""
            SELECT u.*, o.org_name FROM app_user u
            JOIN organization o ON o.org_id = u.org_id
            WHERE u.username ILIKE #{filter} OR u.display_name ILIKE #{filter} OR u.phone ILIKE #{filter}
            ORDER BY u.created_at DESC LIMIT #{limit} OFFSET #{offset}
            """)
    List<PlatformUser> findUsers(
            @Param("filter") String filter,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Select("""
            SELECT count(*) FROM app_user
            WHERE username ILIKE #{filter} OR display_name ILIKE #{filter} OR phone ILIKE #{filter}
            """)
    long countUsers(@Param("filter") String filter);

    @Select("""
            SELECT u.*, o.org_name FROM app_user u
            JOIN organization o ON o.org_id = u.org_id WHERE u.user_id = #{userId}
            """)
    PlatformUser findUser(@Param("userId") String userId);

    @Insert("""
            INSERT INTO app_user (
                user_id, username, password_hash, display_name, phone, org_id, role, status
            ) VALUES (
                #{userId}, #{username}, #{passwordHash}, #{displayName}, #{phone}, #{orgId}, #{role}, #{status}
            )
            """)
    int insertUser(
            @Param("userId") String userId,
            @Param("username") String username,
            @Param("passwordHash") String passwordHash,
            @Param("displayName") String displayName,
            @Param("phone") String phone,
            @Param("orgId") String orgId,
            @Param("role") String role,
            @Param("status") String status
    );

    @Update("""
            UPDATE app_user SET username = #{username}, display_name = #{displayName},
                phone = #{phone}, org_id = #{orgId}, role = #{role}, status = #{status}, updated_at = now()
            WHERE user_id = #{userId}
            """)
    int updateUser(
            @Param("userId") String userId,
            @Param("username") String username,
            @Param("displayName") String displayName,
            @Param("phone") String phone,
            @Param("orgId") String orgId,
            @Param("role") String role,
            @Param("status") String status
    );

    @Update("UPDATE app_user SET password_hash = #{hash}, updated_at = now() WHERE user_id = #{userId}")
    int updatePassword(@Param("userId") String userId, @Param("hash") String passwordHash);

    @Update("UPDATE app_user SET last_login_at = now() WHERE username = #{username}")
    int updateLastLogin(@Param("username") String username);

    @Select("""
            SELECT * FROM audit_log
            WHERE (username ILIKE #{filter} OR resource_type ILIKE #{filter} OR request_path ILIKE #{filter})
              AND action ILIKE #{action}
            ORDER BY created_at DESC LIMIT #{limit} OFFSET #{offset}
            """)
    List<AuditLog> findAuditLogs(
            @Param("filter") String filter,
            @Param("action") String action,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Select("""
            SELECT count(*) FROM audit_log
            WHERE (username ILIKE #{filter} OR resource_type ILIKE #{filter} OR request_path ILIKE #{filter})
              AND action ILIKE #{action}
            """)
    long countAuditLogs(@Param("filter") String filter, @Param("action") String action);

    @Insert("""
            INSERT INTO audit_log (
                user_id, username, org_id, action, resource_type, resource_id,
                request_method, request_path, client_ip, status_code, duration_ms, detail
            ) VALUES (
                #{userId}, #{username}, #{orgId}, #{action}, #{resourceType}, #{resourceId},
                #{method}, #{path}, #{ip}, #{statusCode}, #{durationMs}, CAST(#{detail} AS jsonb)
            )
            """)
    int insertAudit(
            @Param("userId") String userId,
            @Param("username") String username,
            @Param("orgId") String orgId,
            @Param("action") String action,
            @Param("resourceType") String resourceType,
            @Param("resourceId") String resourceId,
            @Param("method") String method,
            @Param("path") String path,
            @Param("ip") String ip,
            @Param("statusCode") int statusCode,
            @Param("durationMs") long durationMs,
            @Param("detail") String detail
    );

    @Insert("""
            INSERT INTO app_user (
                user_id, username, password_hash, display_name, phone, org_id, role, status
            ) VALUES (
                #{userId}, #{username}, #{passwordHash}, #{displayName}, #{phone}, #{orgId}, #{role}, 'ACTIVE'
            ) ON CONFLICT (username) DO NOTHING
            """)
    int insertMockUser(
            @Param("userId") String userId,
            @Param("username") String username,
            @Param("passwordHash") String passwordHash,
            @Param("displayName") String displayName,
            @Param("phone") String phone,
            @Param("orgId") String orgId,
            @Param("role") String role
    );

    @Insert("""
            INSERT INTO organization (org_id, parent_org_id, org_name, org_type, city_code, status)
            VALUES (#{id}, #{parentId}, #{name}, #{type}, #{cityCode}, 'ACTIVE')
            ON CONFLICT (org_id) DO NOTHING
            """)
    int insertMockOrganization(
            @Param("id") String id,
            @Param("parentId") String parentId,
            @Param("name") String name,
            @Param("type") String type,
            @Param("cityCode") String cityCode
    );

    record AuthenticatedUserRow(
            String userId,
            String username,
            String passwordHash,
            String displayName,
            String orgId,
            String orgName,
            UserRole role,
            RecordStatus status
    ) {
    }
}

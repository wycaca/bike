package cn.bike.platform.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class AdminModels {

    private AdminModels() {
    }

    public enum OrganizationType { COMPANY, REGION, TEAM }

    public enum RecordStatus { ACTIVE, DISABLED }

    public enum UserRole { ADMIN, OPERATOR, AUDITOR }

    public record Organization(
            String orgId,
            String parentOrgId,
            String orgName,
            OrganizationType orgType,
            String cityCode,
            RecordStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record OrganizationRequest(
            String parentOrgId,
            @NotBlank @Size(max = 64) String orgName,
            @NotNull OrganizationType orgType,
            @Pattern(regexp = "^$|^[0-9]{6}$", message = "cityCode 必须为空或 6 位行政区代码") String cityCode,
            @NotNull RecordStatus status
    ) {
    }

    public record PlatformUser(
            String userId,
            String username,
            String displayName,
            String phone,
            String orgId,
            String orgName,
            UserRole role,
            RecordStatus status,
            Instant lastLoginAt,
            Instant createdAt
    ) {
    }

    public record UserRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9._-]{3,32}$", message = "用户名需为 3 到 32 位字母、数字或 ._-") String username,
            @NotBlank @Size(max = 64) String displayName,
            @Pattern(regexp = "^$|^1[3-9][0-9]{9}$", message = "手机号格式不正确") String phone,
            @NotBlank String orgId,
            @NotNull UserRole role,
            @NotNull RecordStatus status,
            @Size(min = 8, max = 64) String password
    ) {
    }

    public record PasswordResetRequest(@NotBlank @Size(min = 8, max = 64) String password) {
    }

    public record UserPage(List<PlatformUser> items, long total, int page, int pageSize) {
    }

    public record AuditLog(
            long auditId,
            String userId,
            String username,
            String orgId,
            String action,
            String resourceType,
            String resourceId,
            String requestMethod,
            String requestPath,
            String clientIp,
            int statusCode,
            long durationMs,
            String detail,
            Instant createdAt
    ) {
    }

    public record AuditPage(List<AuditLog> items, long total, int page, int pageSize) {
    }
}

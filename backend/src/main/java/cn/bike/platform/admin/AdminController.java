package cn.bike.platform.admin;

import cn.bike.platform.admin.AdminModels.AuditPage;
import cn.bike.platform.admin.AdminModels.Organization;
import cn.bike.platform.admin.AdminModels.OrganizationRequest;
import cn.bike.platform.admin.AdminModels.PasswordResetRequest;
import cn.bike.platform.admin.AdminModels.PlatformUser;
import cn.bike.platform.admin.AdminModels.UserPage;
import cn.bike.platform.admin.AdminModels.UserRequest;
import cn.bike.platform.common.ApiResponse;
import cn.bike.platform.security.PlatformPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminService service;

    public AdminController(AdminService service) {
        this.service = service;
    }

    /** 输入: 无; 输出: 全部组织。 */
    @GetMapping("/organizations")
    public ApiResponse<List<Organization>> findOrganizations(
            @AuthenticationPrincipal PlatformPrincipal operator
    ) {
        return ApiResponse.ok(service.findOrganizations(operator));
    }

    /** 输入: 组织请求; 输出: 新建组织。 */
    @PostMapping("/organizations")
    public ApiResponse<Organization> createOrganization(
            @Valid @RequestBody OrganizationRequest request,
            @AuthenticationPrincipal PlatformPrincipal operator
    ) {
        return ApiResponse.ok(service.createOrganization(request, operator));
    }

    /** 输入: 组织编号和请求; 输出: 更新后的组织。 */
    @PutMapping("/organizations/{orgId}")
    public ApiResponse<Organization> updateOrganization(
            @PathVariable String orgId,
            @Valid @RequestBody OrganizationRequest request,
            @AuthenticationPrincipal PlatformPrincipal operator
    ) {
        return ApiResponse.ok(service.updateOrganization(orgId, request, operator));
    }

    /** 输入: 用户分页条件; 输出: 用户分页。 */
    @GetMapping("/users")
    public ApiResponse<UserPage> findUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @AuthenticationPrincipal PlatformPrincipal operator
    ) {
        return ApiResponse.ok(service.findUsers(page, pageSize, keyword, operator));
    }

    /** 输入: 用户请求; 输出: 新建用户。 */
    @PostMapping("/users")
    public ApiResponse<PlatformUser> createUser(
            @Valid @RequestBody UserRequest request,
            @AuthenticationPrincipal PlatformPrincipal operator
    ) {
        return ApiResponse.ok(service.createUser(request, operator));
    }

    /** 输入: 用户编号、请求和当前操作者; 输出: 更新后的用户。 */
    @PutMapping("/users/{userId}")
    public ApiResponse<PlatformUser> updateUser(
            @PathVariable String userId,
            @Valid @RequestBody UserRequest request,
            @AuthenticationPrincipal PlatformPrincipal operator
    ) {
        return ApiResponse.ok(service.updateUser(userId, request, operator));
    }

    /** 输入: 用户编号和新密码; 输出: 空成功响应。 */
    @PutMapping("/users/{userId}/password")
    public ApiResponse<Void> resetPassword(
            @PathVariable String userId,
            @Valid @RequestBody PasswordResetRequest request,
            @AuthenticationPrincipal PlatformPrincipal operator
    ) {
        service.resetPassword(userId, request, operator);
        return ApiResponse.ok(null);
    }

    /** 输入: 审计分页和筛选条件; 输出: 审计日志分页。 */
    @GetMapping("/audit-logs")
    public ApiResponse<AuditPage> findAuditLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String action,
            @AuthenticationPrincipal PlatformPrincipal operator
    ) {
        return ApiResponse.ok(service.findAuditLogs(page, pageSize, keyword, action, operator));
    }
}

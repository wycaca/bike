package cn.bike.platform.security;

import cn.bike.platform.admin.AdminModels.UserRole;
import cn.bike.platform.common.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    /** 输入: 当前 CSRF 令牌; 输出: 令牌值, 同时触发浏览器 Cookie 写入。 */
    @GetMapping("/csrf")
    public ApiResponse<CsrfResponse> csrf(CsrfToken csrfToken) {
        return ApiResponse.ok(new CsrfResponse(csrfToken.getToken()));
    }

    /** 输入: 当前认证主体; 输出: 当前用户和权限范围。 */
    @GetMapping("/me")
    public ApiResponse<CurrentUser> me(@AuthenticationPrincipal PlatformPrincipal principal) {
        return ApiResponse.ok(CurrentUser.from(principal));
    }

    public record CsrfResponse(String token) {
    }

    public record CurrentUser(
            String userId,
            String username,
            String displayName,
            String orgId,
            String orgName,
            UserRole role
    ) {
        public static CurrentUser from(PlatformPrincipal principal) {
            return new CurrentUser(principal.userId(), principal.username(), principal.displayName(),
                    principal.orgId(), principal.orgName(), principal.role());
        }
    }
}

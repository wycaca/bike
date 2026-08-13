package cn.bike.platform.admin;

import cn.bike.platform.security.PlatformPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Map;

public class AuditFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(AuditFilter.class);

    private final AdminRepository repository;
    private final JsonMapper jsonMapper;

    public AuditFilter(AdminRepository repository, JsonMapper jsonMapper) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
    }

    /** 输入: HTTP 请求与响应链; 输出: 原响应, 并对写请求追加审计日志。 */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        var startedAt = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (shouldAudit(request)) {
                writeAudit(request, response, startedAt);
            }
        }
    }

    private boolean shouldAudit(HttpServletRequest request) {
        var method = request.getMethod();
        return request.getRequestURI().startsWith("/api/v1/")
                && (method.equals("POST") || method.equals("PUT")
                || method.equals("PATCH") || method.equals("DELETE"));
    }

    private void writeAudit(HttpServletRequest request, HttpServletResponse response, long startedAt) {
        try {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            var principal = platformPrincipal(authentication);
            var segments = request.getRequestURI().split("/");
            // 标准路径为 /api/v1/{module}/{resource}/{id}, 审计资源优先记录可操作实体。
            var resourceType = segments.length > 4 ? segments[4]
                    : segments.length > 3 ? segments[3] : "unknown";
            var resourceId = segments.length > 5 ? segments[5] : null;
            var durationMs = Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
            repository.insertAudit(
                    principal == null ? null : principal.userId(),
                    principal == null ? "anonymous" : principal.username(),
                    principal == null ? null : principal.orgId(),
                    action(request.getMethod()), resourceType, resourceId,
                    request.getMethod(), request.getRequestURI(), clientIp(request),
                    response.getStatus(), durationMs,
                    jsonMapper.writeValueAsString(Map.of("query", request.getQueryString() == null ? "" : request.getQueryString())));
        } catch (RuntimeException exception) {
            // 审计故障不能覆盖原业务响应；记录不含请求正文的定位字段，避免日志泄露密码或业务敏感数据。
            LOG.error("审计日志写入失败 method={} path={} status={} principal={} errorType={}",
                    request.getMethod(), request.getRequestURI(), response.getStatus(),
                    auditPrincipalName(), exception.getClass().getSimpleName(), exception);
        }
    }

    /** 输入: 当前安全上下文; 输出: 用于故障日志定位的用户名，不存在时为 anonymous。 */
    private String auditPrincipalName() {
        var principal = platformPrincipal(SecurityContextHolder.getContext().getAuthentication());
        return principal == null ? "anonymous" : principal.username();
    }

    private PlatformPrincipal platformPrincipal(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof PlatformPrincipal principal
                ? principal : null;
    }

    private String action(String method) {
        return switch (method) {
            case "POST" -> "CREATE";
            case "PUT", "PATCH" -> "UPDATE";
            case "DELETE" -> "DELETE";
            default -> "ACCESS";
        };
    }

    private String clientIp(HttpServletRequest request) {
        var forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank()
                ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
    }
}

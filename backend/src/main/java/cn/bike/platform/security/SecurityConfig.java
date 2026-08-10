package cn.bike.platform.security;

import cn.bike.platform.admin.AdminRepository;
import cn.bike.platform.admin.AuditFilter;
import cn.bike.platform.common.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.InvalidCsrfTokenException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

@Configuration
public class SecurityConfig {

    /** 输入: 无; 输出: BCrypt 密码编码器。 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** 输入: 审计仓储和 JSON 映射器; 输出: 写操作审计过滤器。 */
    @Bean
    public AuditFilter auditFilter(AdminRepository repository, JsonMapper jsonMapper) {
        return new AuditFilter(repository, jsonMapper);
    }

    /** 输入: Spring Security 构建器和应用依赖; 输出: 会话、CSRF 与角色权限过滤链。 */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JsonMapper jsonMapper,
            AdminRepository repository,
            AuditFilter auditFilter
    ) throws Exception {
        var csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookiePath("/");
        var csrfHandler = new CsrfTokenRequestAttributeHandler();

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(csrfHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/api/v1/auth/csrf", "/api/v1/auth/login").permitAll()
                        .requestMatchers("/api/v1/admin/audit-logs/**").hasAnyRole("ADMIN", "AUDITOR")
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/geo/**").hasAnyRole("ADMIN", "OPERATOR")
                        .requestMatchers("/api/v1/reports/**").hasAnyRole("ADMIN", "OPERATOR", "AUDITOR")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginProcessingUrl("/api/v1/auth/login")
                        .successHandler((request, response, authentication) -> {
                            repository.updateLastLogin(authentication.getName());
                            writeJson(response, HttpServletResponse.SC_OK,
                                    ApiResponse.ok(AuthController.CurrentUser.from(
                                            (PlatformPrincipal) authentication.getPrincipal())), jsonMapper);
                        })
                        .failureHandler((request, response, exception) -> writeJson(
                                response, HttpServletResponse.SC_UNAUTHORIZED,
                                ApiResponse.error(40101, "用户名或密码错误"), jsonMapper)))
                .logout(logout -> logout
                        .logoutUrl("/api/v1/auth/logout")
                        .logoutSuccessHandler((request, response, authentication) -> writeJson(
                                response, HttpServletResponse.SC_OK, ApiResponse.ok(null), jsonMapper))
                        .invalidateHttpSession(true)
                        .deleteCookies("SESSION"))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> writeJson(
                                response, HttpServletResponse.SC_UNAUTHORIZED,
                                ApiResponse.error(40100, "请先登录"), jsonMapper))
                        .accessDeniedHandler((request, response, exception) -> writeJson(
                                response, HttpServletResponse.SC_FORBIDDEN,
                                ApiResponse.error(40300,
                                        exception instanceof MissingCsrfTokenException
                                                || exception instanceof InvalidCsrfTokenException
                                                ? "安全令牌已失效，请刷新页面重试" : "没有操作权限"),
                                jsonMapper)))
                .addFilterAfter(auditFilter, AuthorizationFilter.class);
        return http.build();
    }

    private static void writeJson(HttpServletResponse response, int status, Object body, JsonMapper jsonMapper)
            throws java.io.IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), body);
    }
}

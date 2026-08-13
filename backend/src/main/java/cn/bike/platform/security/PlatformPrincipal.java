package cn.bike.platform.security;

import cn.bike.platform.admin.AdminModels.UserRole;
import cn.bike.platform.admin.AdminModels.DataScope;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;

public record PlatformPrincipal(
        String userId,
        String username,
        String password,
        String displayName,
        String orgId,
        String orgName,
        UserRole role,
        DataScope dataScope,
        boolean enabled
) implements UserDetails, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 输入: 无; 输出: Spring Security 登录用户名。 */
    @Override
    public String getUsername() {
        return username;
    }

    /** 输入: 无; 输出: BCrypt 密码摘要。 */
    @Override
    public String getPassword() {
        return password;
    }

    /** 输入: 无; 输出: 当前用户的 Spring Security 角色。 */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    /** 输入: 无; 输出: 账号是否未过期。 */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /** 输入: 无; 输出: 账号是否未锁定。 */
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    /** 输入: 无; 输出: 凭据是否未过期。 */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /** 输入: 无; 输出: 当前账号是否允许登录。 */
    @Override
    public boolean isEnabled() {
        return enabled;
    }
}

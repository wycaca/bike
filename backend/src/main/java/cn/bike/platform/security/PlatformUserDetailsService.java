package cn.bike.platform.security;

import cn.bike.platform.admin.AdminModels.RecordStatus;
import cn.bike.platform.admin.AdminRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class PlatformUserDetailsService implements UserDetailsService {

    private final AdminRepository repository;

    public PlatformUserDetailsService(AdminRepository repository) {
        this.repository = repository;
    }

    /** 输入: 登录用户名; 输出: Spring Security 用户主体。 */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var user = repository.findAuthenticatedUser(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户名或密码错误"));
        return new PlatformPrincipal(
                user.userId(), user.username(), user.passwordHash(), user.displayName(),
                user.orgId(), user.orgName(), user.role(), user.status() == RecordStatus.ACTIVE);
    }
}

package cn.bike.platform.admin;

import cn.bike.platform.admin.AdminModels.AuditPage;
import cn.bike.platform.admin.AdminModels.Organization;
import cn.bike.platform.admin.AdminModels.OrganizationRequest;
import cn.bike.platform.admin.AdminModels.PasswordResetRequest;
import cn.bike.platform.admin.AdminModels.PlatformUser;
import cn.bike.platform.admin.AdminModels.UserPage;
import cn.bike.platform.admin.AdminModels.UserRequest;
import cn.bike.platform.common.NotFoundException;
import cn.bike.platform.security.PlatformPrincipal;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AdminService {

    private final AdminRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    public AdminService(
            AdminRepository repository,
            PasswordEncoder passwordEncoder,
            FindByIndexNameSessionRepository<? extends Session> sessionRepository
    ) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.sessionRepository = sessionRepository;
    }

    /** 输入: 无; 输出: 全部组织。 */
    public List<Organization> findOrganizations() {
        return repository.findOrganizations();
    }

    /** 输入: 组织请求; 输出: 新建组织。 */
    @Transactional
    public Organization createOrganization(OrganizationRequest request) {
        validateOrganization(null, request);
        var orgId = "ORG-" + UUID.randomUUID().toString().substring(0, 12);
        try {
            repository.insertOrganization(orgId, request);
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("同一上级组织下名称不能重复");
        }
        return repository.findOrganization(orgId).orElseThrow();
    }

    /** 输入: 组织编号和请求; 输出: 更新后的组织。 */
    @Transactional
    public Organization updateOrganization(String orgId, OrganizationRequest request) {
        validateOrganization(orgId, request);
        if (repository.updateOrganization(orgId, request) == 0) {
            throw new NotFoundException("组织不存在: " + orgId);
        }
        return repository.findOrganization(orgId).orElseThrow();
    }

    /** 输入: 分页和关键字; 输出: 用户分页。 */
    public UserPage findUsers(int page, int pageSize, String keyword) {
        validatePage(page, pageSize);
        return new UserPage(repository.findUsers(page, pageSize, keyword),
                repository.countUsers(keyword), page, pageSize);
    }

    /** 输入: 用户请求; 输出: 新建用户。 */
    @Transactional
    public PlatformUser createUser(UserRequest request) {
        validateUserRequest(request, true);
        var userId = "USR-" + UUID.randomUUID().toString().substring(0, 12);
        try {
            repository.insertUser(userId, request, passwordEncoder.encode(request.password()));
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("用户名已存在");
        }
        return repository.findUser(userId).orElseThrow();
    }

    /** 输入: 用户编号和请求; 输出: 更新后的用户。 */
    @Transactional
    public PlatformUser updateUser(String userId, UserRequest request, PlatformPrincipal operator) {
        validateUserRequest(request, false);
        if (operator.userId().equals(userId) && request.status() != AdminModels.RecordStatus.ACTIVE) {
            throw new IllegalArgumentException("不能停用当前登录账号");
        }
        var previous = repository.findUser(userId)
                .orElseThrow(() -> new NotFoundException("用户不存在: " + userId));
        try {
            if (repository.updateUser(userId, request) == 0) {
                throw new NotFoundException("用户不存在: " + userId);
            }
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("用户名已存在");
        }
        var updated = repository.findUser(userId).orElseThrow();
        revokeSessions(previous.username());
        if (!previous.username().equals(updated.username())) {
            revokeSessions(updated.username());
        }
        return updated;
    }

    /** 输入: 用户编号和新密码; 输出: 无。 */
    public void resetPassword(String userId, PasswordResetRequest request) {
        var user = repository.findUser(userId)
                .orElseThrow(() -> new NotFoundException("用户不存在: " + userId));
        if (repository.updatePassword(userId, passwordEncoder.encode(request.password())) == 0) {
            throw new NotFoundException("用户不存在: " + userId);
        }
        revokeSessions(user.username());
    }

    /** 输入: 审计分页条件; 输出: 审计日志分页。 */
    public AuditPage findAuditLogs(int page, int pageSize, String keyword, String action) {
        validatePage(page, pageSize);
        return new AuditPage(repository.findAuditLogs(page, pageSize, keyword, action),
                repository.countAuditLogs(keyword, action), page, pageSize);
    }

    private void validateOrganization(String orgId, OrganizationRequest request) {
        if (orgId != null && orgId.equals(request.parentOrgId())) {
            throw new IllegalArgumentException("组织不能把自己设为上级");
        }
        if (request.parentOrgId() != null && !request.parentOrgId().isBlank()) {
            var parent = repository.findOrganization(request.parentOrgId())
                    .orElseThrow(() -> new IllegalArgumentException("上级组织不存在"));
            // 从拟选上级逐层向根回溯，命中当前组织即说明会形成环。
            while (parent != null) {
                if (orgId != null && (orgId.equals(parent.orgId()) || orgId.equals(parent.parentOrgId()))) {
                    throw new IllegalArgumentException("上级组织不能是当前组织的下级");
                }
                parent = parent.parentOrgId() == null ? null
                        : repository.findOrganization(parent.parentOrgId()).orElse(null);
            }
        }
    }

    private void validateUserRequest(UserRequest request, boolean creating) {
        if (repository.findOrganization(request.orgId()).isEmpty()) {
            throw new IllegalArgumentException("所属组织不存在");
        }
        if (creating && (request.password() == null || request.password().isBlank())) {
            throw new IllegalArgumentException("新建用户必须设置初始密码");
        }
    }

    private void validatePage(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("page 必须大于 0, pageSize 必须在 1 到 100 之间");
        }
    }

    /** 输入: 登录用户名; 输出: 无，删除该主体当前保存的全部 Redis 会话。 */
    private void revokeSessions(String username) {
        sessionRepository.findByPrincipalName(username).keySet().forEach(sessionRepository::deleteById);
    }
}

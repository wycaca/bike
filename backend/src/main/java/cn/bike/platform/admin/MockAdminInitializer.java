package cn.bike.platform.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mock 环境的组织和管理员基础数据初始化器.
 * 初始化 SQL 可以重复执行, 避免开发环境重启产生重复基础数据.
 */
@Profile("mock")
@Component
@Order(10)
public class MockAdminInitializer implements ApplicationRunner {

    private final AdminMapper mapper;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;

    public MockAdminInitializer(
            AdminMapper mapper,
            PasswordEncoder passwordEncoder,
            @Value("${app.bootstrap-admin.username}") String username,
            @Value("${app.bootstrap-admin.password}") String password
    ) {
        this.mapper = mapper;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
    }

    /** 输入: Spring Boot 启动参数; 输出: 无, 幂等创建 Mock 组织与开发管理员。 */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        insertOrganization("ORG-HQ", null, "运营总部", "COMPANY", null);
        insertOrganization("ORG-BJ", "ORG-HQ", "北京运营中心", "REGION", "110000");
        insertOrganization("ORG-SH", "ORG-HQ", "上海运营中心", "REGION", "310000");
        var passwordHash = passwordEncoder.encode(password);
        insertUser("USR-ADMIN", username, passwordHash, "系统管理员", null, "ORG-HQ", "ADMIN");
        insertUser("USR-OP-BJ", "operator.bj", passwordHash, "北京运维一组", "13800001101", "ORG-BJ", "OPERATOR");
        insertUser("USR-OP-SH", "operator.sh", passwordHash, "上海运维一组", "13800003101", "ORG-SH", "OPERATOR");
    }

    /** 输入: 用户基础信息; 输出: 无, 用户名存在时保持已有账号。 */
    private void insertUser(
            String userId,
            String userName,
            String passwordHash,
            String displayName,
            String phone,
            String orgId,
            String role
    ) {
        mapper.insertMockUser(userId, userName, passwordHash, displayName, phone, orgId, role);
    }

    /** 输入: 组织基础信息; 输出: 无, 组织编号存在时保持已有数据。 */
    private void insertOrganization(String id, String parentId, String name, String type, String cityCode) {
        mapper.insertMockOrganization(id, parentId, name, type, cityCode);
    }
}

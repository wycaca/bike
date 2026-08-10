package cn.bike.platform.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Profile("mock")
@Component
@Order(10)
public class MockAdminInitializer implements ApplicationRunner {

    private final JdbcClient jdbcClient;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;

    public MockAdminInitializer(
            JdbcClient jdbcClient,
            PasswordEncoder passwordEncoder,
            @Value("${app.bootstrap-admin.username}") String username,
            @Value("${app.bootstrap-admin.password}") String password
    ) {
        this.jdbcClient = jdbcClient;
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
        jdbcClient.sql("""
                        INSERT INTO app_user (
                            user_id, username, password_hash, display_name, org_id, role, status
                        ) VALUES ('USR-ADMIN', :username, :passwordHash, '系统管理员', 'ORG-HQ', 'ADMIN', 'ACTIVE')
                        ON CONFLICT (username) DO NOTHING
                        """)
                .param("username", username)
                .param("passwordHash", passwordEncoder.encode(password))
                .update();
    }

    private void insertOrganization(String id, String parentId, String name, String type, String cityCode) {
        jdbcClient.sql("""
                        INSERT INTO organization (org_id, parent_org_id, org_name, org_type, city_code, status)
                        VALUES (:id, :parentId, :name, :type, :cityCode, 'ACTIVE')
                        ON CONFLICT (org_id) DO NOTHING
                        """)
                .param("id", id).param("parentId", parentId).param("name", name)
                .param("type", type).param("cityCode", cityCode).update();
    }
}

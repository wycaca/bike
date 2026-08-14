package cn.bike.platform.security;

import cn.bike.platform.admin.AdminRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.json.JsonMapper;

import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

class SecurityConfigTest {

    private AnnotationConfigWebApplicationContext context;
    private MockMvc mockMvc;

    @BeforeEach
    void 创建安全过滤链测试客户端() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestConfiguration.class);
        context.refresh();
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void 关闭测试上下文() {
        context.close();
    }

    @Test
    void 雅迪模拟写接口只允许管理员调用() throws Exception {
        mockMvc.perform(post("/api/v1/mock/yadea/events")
                        .with(csrf()).with(user("auditor").roles("AUDITOR")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/mock/yadea/events")
                        .with(csrf()).with(user("admin").roles("ADMIN")))
                .andExpect(status().isAccepted());
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import(SecurityConfig.class)
    static class TestConfiguration {

        @Bean
        AdminRepository adminRepository() {
            return mock(AdminRepository.class);
        }

        @Bean
        JsonMapper jsonMapper() {
            return JsonMapper.builder().build();
        }

        @Bean
        MockYadeaController mockYadeaController() {
            return new MockYadeaController();
        }
    }

    @RestController
    static class MockYadeaController {

        @PostMapping("/api/v1/mock/yadea/events")
        ResponseEntity<Void> acceptEvent() {
            return ResponseEntity.accepted().build();
        }
    }
}

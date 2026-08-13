package cn.bike.platform.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class ApiExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void 创建测试客户端() {
        mockMvc = standaloneSetup(new ContractController())
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void 参数类型和请求体错误应返回统一响应() throws Exception {
        mockMvc.perform(get("/contract/number").param("value", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40002));

        mockMvc.perform(post("/contract/body").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40002));
    }

    @Test
    void Bean校验错误应返回字段消息() throws Exception {
        mockMvc.perform(post("/contract/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("name:")));
    }

    @Test
    void 业务异常应保留对应HTTP状态() throws Exception {
        mockMvc.perform(get("/contract/error/not-found"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(40400));
        mockMvc.perform(get("/contract/error/conflict"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(40900));
        mockMvc.perform(get("/contract/error/forbidden"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40300));
    }

    @Test
    void 上传超限和未知异常应隐藏内部细节() throws Exception {
        mockMvc.perform(get("/contract/error/oversized"))
                .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.code").value(41300));
        mockMvc.perform(get("/contract/error/internal"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(50000))
                .andExpect(jsonPath("$.message").value("服务暂时不可用"));
    }

    @Test
    void 不支持的请求方法应返回Allow头() throws Exception {
        mockMvc.perform(post("/contract/number").param("value", "1"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", org.hamcrest.Matchers.containsString("GET")))
                .andExpect(jsonPath("$.code").value(40500));
    }

    @RestController
    static class ContractController {

        @GetMapping("/contract/number")
        ApiResponse<Integer> number(@RequestParam int value) {
            return ApiResponse.ok(value);
        }

        @PostMapping("/contract/body")
        ApiResponse<String> body(@Valid @RequestBody TestRequest request) {
            return ApiResponse.ok(request.name());
        }

        @GetMapping("/contract/error/{type}")
        ApiResponse<Void> error(@PathVariable String type) {
            throw switch (type) {
                case "not-found" -> new NotFoundException("资源不存在");
                case "conflict" -> new ConflictException("资源冲突");
                case "forbidden" -> new AccessDeniedException("禁止访问");
                case "oversized" -> new MaxUploadSizeExceededException(8L * 1024 * 1024);
                default -> new IllegalStateException("数据库连接信息不应返回客户端");
            };
        }
    }

    record TestRequest(@NotBlank String name) {
    }
}

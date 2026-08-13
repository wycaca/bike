package cn.bike.platform.admin;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class AuditFilterTest {

    @Test
    void 审计写入失败应记录日志且不覆盖业务响应() {
        var repository = mock(AdminRepository.class);
        doThrow(new IllegalStateException("database unavailable")).when(repository).insertAudit(
                any(), any(), any(), any(), any(), any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyLong(), any());
        var filter = new AuditFilter(repository, JsonMapper.builder().build());
        var request = new MockHttpServletRequest("POST", "/api/v1/admin/vehicles");
        var response = new MockHttpServletResponse();
        var logger = (Logger) LoggerFactory.getLogger(AuditFilter.class);
        var appender = new ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertDoesNotThrow(() -> filter.doFilterInternal(
                    request, response, (ignoredRequest, servletResponse) ->
                            ((MockHttpServletResponse) servletResponse).setStatus(201)));

            assertThat(response.getStatus()).isEqualTo(201);
            assertThat(appender.list).anySatisfy(event -> {
                assertThat(event.getFormattedMessage()).contains(
                        "审计日志写入失败", "method=POST", "path=/api/v1/admin/vehicles", "status=201");
                assertThat(event.getThrowableProxy()).isNotNull();
            });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}

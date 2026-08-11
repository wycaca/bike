package cn.bike.platform.dashboard;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;

class DashboardServiceTest {

    @Test
    void 趋势天数超出范围时应拒绝请求() {
        var service = new DashboardService(mock(DashboardRepository.class));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.dashboard("110000", 32))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 到 31");
    }
}

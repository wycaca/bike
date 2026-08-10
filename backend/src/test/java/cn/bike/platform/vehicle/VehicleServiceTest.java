package cn.bike.platform.vehicle;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VehicleServiceTest {

    @Test
    void 缩放级别越高聚合网格越小() {
        assertThat(VehicleService.gridSizeForZoom(15))
                .isLessThan(VehicleService.gridSizeForZoom(10));
    }
}

package cn.bike.platform.vehicle;

import cn.bike.platform.vehicle.VehicleModels.CoordinateSystem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CoordinateConverterTest {

    @Test
    void 北京坐标转换后可稳定反算() {
        var longitude = new BigDecimal("116.397389");
        var latitude = new BigDecimal("39.908722");

        var gcj02 = CoordinateConverter.convert(
                longitude, latitude, CoordinateSystem.WGS84, CoordinateSystem.GCJ02);
        var restored = CoordinateConverter.convert(
                gcj02.longitude(), gcj02.latitude(), CoordinateSystem.GCJ02, CoordinateSystem.WGS84);

        assertThat(gcj02.longitude()).isBetween(
                new BigDecimal("116.4035"), new BigDecimal("116.4039"));
        assertThat(gcj02.latitude()).isBetween(
                new BigDecimal("39.9099"), new BigDecimal("39.9103"));
        assertThat(restored.longitude().subtract(longitude).abs())
                .isLessThan(new BigDecimal("0.000001"));
        assertThat(restored.latitude().subtract(latitude).abs())
                .isLessThan(new BigDecimal("0.000001"));
    }

    @Test
    void 中国大陆范围外保持原坐标() {
        var longitude = new BigDecimal("139.691700");
        var latitude = new BigDecimal("35.689500");

        var converted = CoordinateConverter.convert(
                longitude, latitude, CoordinateSystem.WGS84, CoordinateSystem.GCJ02);

        assertThat(converted.longitude()).isEqualByComparingTo(longitude);
        assertThat(converted.latitude()).isEqualByComparingTo(latitude);
    }
}

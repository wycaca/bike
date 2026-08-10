package cn.bike.platform.vehicle;

import cn.bike.platform.vehicle.VehicleModels.CoordinateSystem;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 在平台持久化坐标和中国大陆地图展示坐标之间转换. 转换只用于接口输出和视野查询, 不修改原始数据.
 */
final class CoordinateConverter {

    private static final double PI = Math.PI;
    private static final double AXIS = 6378245.0;
    private static final double ECCENTRICITY = 0.00669342162296594323;
    private static final int OUTPUT_SCALE = 7;

    private CoordinateConverter() {
    }

    static Coordinate convert(
            BigDecimal longitude,
            BigDecimal latitude,
            CoordinateSystem source,
            CoordinateSystem target
    ) {
        if (source == target || outsideChina(longitude.doubleValue(), latitude.doubleValue())) {
            return new Coordinate(longitude, latitude);
        }
        if (source == CoordinateSystem.WGS84) {
            return wgs84ToGcj02(longitude.doubleValue(), latitude.doubleValue());
        }
        return gcj02ToWgs84(longitude.doubleValue(), latitude.doubleValue());
    }

    private static Coordinate wgs84ToGcj02(double longitude, double latitude) {
        var latitudeOffset = transformLatitude(longitude - 105.0, latitude - 35.0);
        var longitudeOffset = transformLongitude(longitude - 105.0, latitude - 35.0);
        var radianLatitude = latitude / 180.0 * PI;
        var magic = Math.sin(radianLatitude);
        magic = 1 - ECCENTRICITY * magic * magic;
        var squareRootMagic = Math.sqrt(magic);
        latitudeOffset = latitudeOffset * 180.0
                / ((AXIS * (1 - ECCENTRICITY)) / (magic * squareRootMagic) * PI);
        longitudeOffset = longitudeOffset * 180.0
                / (AXIS / squareRootMagic * Math.cos(radianLatitude) * PI);
        return coordinate(longitude + longitudeOffset, latitude + latitudeOffset);
    }

    private static Coordinate gcj02ToWgs84(double longitude, double latitude) {
        var wgsLongitude = longitude;
        var wgsLatitude = latitude;

        // 迭代消除正向转换误差, 使地图视野反算精度稳定在车辆定位精度以内.
        for (var iteration = 0; iteration < 4; iteration++) {
            var converted = wgs84ToGcj02(wgsLongitude, wgsLatitude);
            wgsLongitude -= converted.longitude().doubleValue() - longitude;
            wgsLatitude -= converted.latitude().doubleValue() - latitude;
        }
        return coordinate(wgsLongitude, wgsLatitude);
    }

    private static double transformLatitude(double longitude, double latitude) {
        var result = -100.0 + 2.0 * longitude + 3.0 * latitude
                + 0.2 * latitude * latitude + 0.1 * longitude * latitude
                + 0.2 * Math.sqrt(Math.abs(longitude));
        result += (20.0 * Math.sin(6.0 * longitude * PI)
                + 20.0 * Math.sin(2.0 * longitude * PI)) * 2.0 / 3.0;
        result += (20.0 * Math.sin(latitude * PI)
                + 40.0 * Math.sin(latitude / 3.0 * PI)) * 2.0 / 3.0;
        result += (160.0 * Math.sin(latitude / 12.0 * PI)
                + 320 * Math.sin(latitude * PI / 30.0)) * 2.0 / 3.0;
        return result;
    }

    private static double transformLongitude(double longitude, double latitude) {
        var result = 300.0 + longitude + 2.0 * latitude
                + 0.1 * longitude * longitude + 0.1 * longitude * latitude
                + 0.1 * Math.sqrt(Math.abs(longitude));
        result += (20.0 * Math.sin(6.0 * longitude * PI)
                + 20.0 * Math.sin(2.0 * longitude * PI)) * 2.0 / 3.0;
        result += (20.0 * Math.sin(longitude * PI)
                + 40.0 * Math.sin(longitude / 3.0 * PI)) * 2.0 / 3.0;
        result += (150.0 * Math.sin(longitude / 12.0 * PI)
                + 300.0 * Math.sin(longitude / 30.0 * PI)) * 2.0 / 3.0;
        return result;
    }

    private static boolean outsideChina(double longitude, double latitude) {
        return longitude < 72.004 || longitude > 137.8347
                || latitude < 0.8293 || latitude > 55.8271;
    }

    private static Coordinate coordinate(double longitude, double latitude) {
        return new Coordinate(
                BigDecimal.valueOf(longitude).setScale(OUTPUT_SCALE, RoundingMode.HALF_UP),
                BigDecimal.valueOf(latitude).setScale(OUTPUT_SCALE, RoundingMode.HALF_UP));
    }

    record Coordinate(BigDecimal longitude, BigDecimal latitude) {
    }
}


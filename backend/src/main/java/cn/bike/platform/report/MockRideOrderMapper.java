package cn.bike.platform.report;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Mock 骑行订单持久化 Mapper.
 * 批量插入只服务开发数据初始化, 生产订单写入不复用该入口.
 */
@Mapper
public interface MockRideOrderMapper {

    long countOrders();

    List<VehicleSeed> findVehicles();

    int insertRideOrders(@Param("rows") List<RideOrderSeed> rows);

    record VehicleSeed(String vehicleId, String cityCode, String areaCode, LocalDate launchDate) {
    }

    record RideOrderSeed(
            String orderId,
            String vehicleId,
            String riderId,
            String cityCode,
            String areaCode,
            Instant startedAt,
            Instant endedAt,
            int durationSeconds,
            int distanceMeters,
            BigDecimal grossAmount,
            BigDecimal discountAmount,
            BigDecimal refundAmount,
            String orderStatus,
            String paymentChannel
    ) {
    }
}

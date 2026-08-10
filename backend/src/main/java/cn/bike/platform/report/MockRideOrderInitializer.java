package cn.bike.platform.report;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Random;

@Profile("mock")
@Component
@Order(40)
public class MockRideOrderInitializer implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(MockRideOrderInitializer.class);
    private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MOCK_DAYS = 120;
    private static final long RANDOM_SEED = 20260810L;
    private final JdbcClient jdbcClient;
    private final JdbcTemplate jdbcTemplate;

    public MockRideOrderInitializer(JdbcClient jdbcClient, JdbcTemplate jdbcTemplate) {
        this.jdbcClient = jdbcClient;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 输入: Spring Boot 启动参数; 输出: 无, 首次启动时生成最近 120 个完整自然日的骑行订单。
     *
     * 步骤:
     * 1. 读取可投放车辆, 固定随机种子以保证不同开发环境的报表可复现。
     * 2. 按工作日、周末和早晚高峰生成骑行, 再根据时长计算阶梯计费。
     * 3. 注入优惠、部分退款、全额退款和取消订单, 覆盖财务报表的常见状态。
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        var existing = jdbcClient.sql("SELECT count(*) FROM ride_order").query(Long.class).single();
        if (existing > 0) {
            LOG.info("骑行订单已存在 {} 条, 跳过 Mock 初始化", existing);
            return;
        }

        var vehicles = jdbcClient.sql("""
                        SELECT vehicle_id, operation_city_code, operation_area_code, launch_date
                        FROM vehicle
                        WHERE lifecycle_status IN ('OPERATING', 'DISPATCHING')
                        ORDER BY vehicle_id
                        """)
                .query((rs, rowNum) -> new VehicleSeed(rs.getString("vehicle_id"),
                        rs.getString("operation_city_code"), rs.getString("operation_area_code"),
                        rs.getObject("launch_date", LocalDate.class))).list();
        if (vehicles.isEmpty()) {
            LOG.warn("没有可投放车辆, 跳过骑行订单 Mock 初始化");
            return;
        }

        var endDate = LocalDate.now(REPORT_ZONE).minusDays(1);
        var startDate = endDate.minusDays(MOCK_DAYS - 1L);
        var random = new Random(RANDOM_SEED);
        var rows = new ArrayList<Object[]>();
        var sequence = 1;

        for (var day = startDate; !day.isAfter(endDate); day = day.plusDays(1)) {
            var weekend = day.getDayOfWeek().getValue() >= 6;
            for (var vehicle : vehicles) {
                if (vehicle.launchDate().isAfter(day) || random.nextDouble() < 0.12) continue;
                var rideCount = 1 + random.nextInt(weekend ? 5 : 4);
                for (var rideIndex = 0; rideIndex < rideCount; rideIndex++) {
                    rows.add(completedRide(sequence++, day, vehicle, rideIndex, random));
                }
                if (random.nextDouble() < 0.035) {
                    rows.add(cancelledRide(sequence++, day, vehicle, random));
                }
            }
        }

        var sql = """
                INSERT INTO ride_order (
                    order_id, vehicle_id, rider_id, city_code, area_code, started_at, ended_at,
                    duration_seconds, distance_meters, gross_amount, discount_amount, refund_amount,
                    order_status, payment_channel
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.batchUpdate(sql, rows);
        LOG.info("已生成 {} 至 {} 的模拟骑行订单 {} 条", startDate, endDate, rows.size());
    }

    private Object[] completedRide(
            int sequence, LocalDate day, VehicleSeed vehicle, int rideIndex, Random random
    ) {
        var hour = rideHour(rideIndex, random);
        var startedAt = day.atTime(hour, random.nextInt(60)).atZone(REPORT_ZONE).toInstant();
        var durationMinutes = 7 + random.nextInt(35);
        var durationSeconds = durationMinutes * 60 + random.nextInt(60);
        var distanceMeters = durationMinutes * (105 + random.nextInt(76));
        var gross = fare(durationMinutes);
        var discount = discount(gross, random);
        var paidBeforeRefund = gross.subtract(discount);
        var refundRoll = random.nextDouble();
        var refund = BigDecimal.ZERO.setScale(2);
        var status = "PAID";
        if (refundRoll < 0.012) {
            refund = paidBeforeRefund;
            status = "REFUNDED";
        } else if (refundRoll < 0.035) {
            refund = paidBeforeRefund.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
            status = "PARTIAL_REFUNDED";
        }
        return new Object[]{orderId(day, sequence), vehicle.vehicleId(), riderId(random),
                vehicle.cityCode(), vehicle.areaCode(), Timestamp.from(startedAt),
                Timestamp.from(startedAt.plusSeconds(durationSeconds)), durationSeconds, distanceMeters,
                gross, discount, refund, status, random.nextBoolean() ? "WECHAT" : "ALIPAY"};
    }

    private Object[] cancelledRide(int sequence, LocalDate day, VehicleSeed vehicle, Random random) {
        var startedAt = day.atTime(10 + random.nextInt(10), random.nextInt(60))
                .atZone(REPORT_ZONE).toInstant();
        var zero = BigDecimal.ZERO.setScale(2);
        return new Object[]{orderId(day, sequence), vehicle.vehicleId(), riderId(random),
                vehicle.cityCode(), vehicle.areaCode(), Timestamp.from(startedAt), null,
                0, 0, zero, zero, zero, "CANCELLED", null};
    }

    private int rideHour(int rideIndex, Random random) {
        if (rideIndex == 0) return 7 + random.nextInt(3);
        if (rideIndex == 1) return 17 + random.nextInt(4);
        return 10 + random.nextInt(12);
    }

    private BigDecimal fare(int durationMinutes) {
        var extraPeriods = Math.max(0, (durationMinutes - 1) / 15);
        return BigDecimal.valueOf(2.5 + extraPeriods * 1.5).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal discount(BigDecimal gross, Random random) {
        var roll = random.nextDouble();
        if (roll < 0.06) return gross.min(BigDecimal.ONE).setScale(2);
        if (roll < 0.28) return gross.min(BigDecimal.valueOf(0.5)).setScale(2);
        return BigDecimal.ZERO.setScale(2);
    }

    private String riderId(Random random) {
        return "RIDER-" + String.format("%05d", 1 + random.nextInt(5000));
    }

    private String orderId(LocalDate day, int sequence) {
        return "RIDE-" + day.toString().replace("-", "") + "-" + String.format("%07d", sequence);
    }

    private record VehicleSeed(String vehicleId, String cityCode, String areaCode, LocalDate launchDate) {
    }
}

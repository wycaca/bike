package cn.bike.platform.vehicle;

import cn.bike.platform.admin.AdminModels.DataScope;
import cn.bike.platform.admin.AdminModels.RecordStatus;
import cn.bike.platform.admin.AdminModels.UserRole;
import cn.bike.platform.city.CityModels.City;
import cn.bike.platform.city.CityRepository;
import cn.bike.platform.security.PlatformPrincipal;
import cn.bike.platform.vehicle.FleetModels.VehicleBatchRequest;
import cn.bike.platform.vehicle.FleetModels.VehicleCreateRequest;
import cn.bike.platform.vehicle.VehicleModels.LifecycleStatus;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FleetServiceTest {

    @Test
    void 批量新增应保留成功行并返回逐行失败原因() {
        var vehicleRepository = mock(VehicleRepository.class);
        var cityRepository = mock(CityRepository.class);
        when(cityRepository.findByCode("440100")).thenReturn(Optional.of(city()));
        when(vehicleRepository.insertVehicle(any())).thenReturn(1, 0);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var service = new FleetService(vehicleRepository, cityRepository, factory.getValidator());
            var valid = request("BIKE-GZ-001", "LOCK-GZ-001", "CTRL-GZ-001", LocalDate.now());
            var duplicate = request("BIKE-GZ-002", "LOCK-GZ-002", "CTRL-GZ-002", LocalDate.now());
            var future = request("BIKE-GZ-003", "LOCK-GZ-003", "CTRL-GZ-003", LocalDate.now().plusDays(1));

            var result = service.createBatch(
                    new VehicleBatchRequest(List.of(valid, duplicate, future)), globalAdmin());

            assertEquals(3, result.requestedCount());
            assertEquals(1, result.createdCount());
            assertEquals(2, result.skippedCount());
            assertEquals(2, result.skipped().getFirst().rowNumber());
            assertEquals(3, result.skipped().getLast().rowNumber());
            verify(vehicleRepository, org.mockito.Mockito.times(2)).insertVehicle(any());
        }
    }

    /** 输入: 车辆唯一编号与日期; 输出: 广州运营车辆新增请求。 */
    private VehicleCreateRequest request(String vehicleId, String lockId, String controllerId, LocalDate date) {
        return new VehicleCreateRequest(
                vehicleId, "COMPANY", lockId, controllerId, null, null, "YD-DEMO", "BATCH-GZ",
                "440100", "440106", date, LifecycleStatus.OPERATING);
    }

    /** 输入: 无; 输出: 启用的广州运营城市配置。 */
    private City city() {
        return new City(
                "440100", "广州", "ORG-GZ", "广州运营中心",
                value("113.2644"), value("23.1291"), value("113.1"), value("23.0"),
                value("113.5"), value("23.3"), RecordStatus.ACTIVE, Instant.now(), Instant.now());
    }

    /** 输入: 数字文本; 输出: 精确十进制。 */
    private BigDecimal value(String value) {
        return new BigDecimal(value);
    }

    /** 输入: 无; 输出: 可维护全平台资产的管理员。 */
    private PlatformPrincipal globalAdmin() {
        return new PlatformPrincipal(
                "USR-1", "admin", "hash", "管理员", "ORG-HQ", "总部",
                UserRole.ADMIN, DataScope.ALL, true);
    }
}

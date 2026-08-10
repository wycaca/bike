package cn.bike.platform.mock;

import cn.bike.platform.telemetry.TelemetryModels.YadeaCloudEvent;
import cn.bike.platform.telemetry.TelemetryProcessor;
import cn.bike.platform.vehicle.VehicleModels.VehicleAsset;
import cn.bike.platform.vehicle.VehicleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

@Profile("mock")
@Component
@Order(30)
public class MockDataInitializer implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(MockDataInitializer.class);
    private static final TypeReference<List<VehicleAsset>> VEHICLE_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<YadeaCloudEvent>> EVENT_LIST_TYPE = new TypeReference<>() {
    };

    private final ResourceLoader resourceLoader;
    private final JsonMapper jsonMapper;
    private final VehicleRepository vehicleRepository;
    private final TelemetryProcessor telemetryProcessor;

    public MockDataInitializer(
            ResourceLoader resourceLoader,
            JsonMapper jsonMapper,
            VehicleRepository vehicleRepository,
            TelemetryProcessor telemetryProcessor
    ) {
        this.resourceLoader = resourceLoader;
        this.jsonMapper = jsonMapper;
        this.vehicleRepository = vehicleRepository;
        this.telemetryProcessor = telemetryProcessor;
    }

    /**
     * 输入: Spring Boot 启动参数; 输出: 无, 将固定车辆及轨迹写入数据库和 Redis.
     *
     * 步骤:
     * 1. 在同一事务内幂等写入车辆和全部历史轨迹, 避免大量逐点事务提交.
     * 2. 轨迹倒序处理, 让每辆车的最新点先更新投影和 Redis; 较旧点只补齐历史表.
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        var vehiclesResource = resourceLoader.getResource("classpath:mock/vehicles.json");
        var eventsResource = resourceLoader.getResource("classpath:mock/yadea-cloud-events.json");

        var vehicles = jsonMapper.readValue(vehiclesResource.getInputStream(), VEHICLE_LIST_TYPE);
        vehicles.forEach(vehicleRepository::upsertVehicle);

        var events = jsonMapper.readValue(eventsResource.getInputStream(), EVENT_LIST_TYPE);
        events.reversed().forEach(telemetryProcessor::process);
        LOG.info("已载入模拟车辆 {} 辆, 雅迪云事件 {} 条", vehicles.size(), events.size());
    }
}

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
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

@Profile("mock")
@Component
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
     * 启动时载入固定 JSON, 保证地图和轨迹测试结果可重复, 不依赖随机数据.
     */
    @Override
    public void run(ApplicationArguments args) throws Exception {
        var vehiclesResource = resourceLoader.getResource("classpath:mock/vehicles.json");
        var eventsResource = resourceLoader.getResource("classpath:mock/yadea-cloud-events.json");

        var vehicles = jsonMapper.readValue(vehiclesResource.getInputStream(), VEHICLE_LIST_TYPE);
        vehicles.forEach(vehicleRepository::upsertVehicle);

        var events = jsonMapper.readValue(eventsResource.getInputStream(), EVENT_LIST_TYPE);
        events.forEach(telemetryProcessor::process);
        LOG.info("已载入模拟车辆 {} 辆, 雅迪云事件 {} 条", vehicles.size(), events.size());
    }
}

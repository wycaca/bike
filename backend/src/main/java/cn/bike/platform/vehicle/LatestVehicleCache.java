package cn.bike.platform.vehicle;

import cn.bike.platform.vehicle.VehicleModels.LatestState;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

@Component
public class LatestVehicleCache {

    private static final String KEY_PREFIX = "vehicle:latest:";

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;

    public LatestVehicleCache(StringRedisTemplate redisTemplate, JsonMapper jsonMapper) {
        this.redisTemplate = redisTemplate;
        this.jsonMapper = jsonMapper;
    }

    /**
     * 保存车辆最新状态. 不设置过期时间, 避免长期静止车辆丢失最后一次有效状态.
     */
    public void put(String vehicleId, LatestState state) {
        redisTemplate.opsForValue().set(KEY_PREFIX + vehicleId, jsonMapper.writeValueAsString(state));
    }

    /**
     * 查询 Redis 中的最新状态. 缓存缺失时由服务层回退到 PostgreSQL 投影表.
     */
    public Optional<LatestState> get(String vehicleId) {
        var json = redisTemplate.opsForValue().get(KEY_PREFIX + vehicleId);
        if (json == null) {
            return Optional.empty();
        }
        return Optional.of(jsonMapper.readValue(json, LatestState.class));
    }
}

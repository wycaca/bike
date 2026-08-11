package cn.bike.platform.geo;

import cn.bike.platform.geo.GeoMapper.FenceRow;
import cn.bike.platform.geo.GeoMapper.ParkingPointRow;
import cn.bike.platform.geo.GeoModels.Coordinate;
import cn.bike.platform.geo.GeoModels.Geofence;
import cn.bike.platform.geo.GeoModels.GeofenceRequest;
import cn.bike.platform.geo.GeoModels.GeoViolation;
import cn.bike.platform.geo.GeoModels.ParkingPoint;
import cn.bike.platform.geo.GeoModels.ParkingPointRequest;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 地理域仓储, 负责空间查询参数和数据库行模型到领域对象的转换.
 * 坐标关系由 Mapper 中的 PostGIS SQL 判断, 不在 Java 中重复实现几何算法.
 */
@Repository
public class GeoRepository {

    private static final TypeReference<GeoJsonPolygon> POLYGON_TYPE = new TypeReference<>() {
    };

    private final GeoMapper mapper;
    private final JsonMapper jsonMapper;

    public GeoRepository(GeoMapper mapper, JsonMapper jsonMapper) {
        this.mapper = mapper;
        this.jsonMapper = jsonMapper;
    }

    /** 输入: 城市代码; 输出: 该城市全部围栏。 */
    public List<Geofence> findFences(String cityCode) {
        return mapper.findFences(cityCode).stream().map(this::mapFence).toList();
    }

    /** 输入: 围栏编号; 输出: 对应围栏。 */
    public Optional<Geofence> findFence(String fenceId) {
        return Optional.ofNullable(mapper.findFence(fenceId)).map(this::mapFence);
    }

    /** 输入: 围栏编号、请求、WKT 和操作者; 输出: 无, 新增围栏。 */
    public void insertFence(String fenceId, GeofenceRequest request, String wkt, String operatorId) {
        mapper.insertFence(fenceId, request.orgId(), request.fenceName().trim(), request.cityCode(),
                request.fenceType().name(), wkt, request.status().name(), operatorId);
    }

    /** 输入: 围栏编号、请求和 WKT; 输出: 受影响行数。 */
    public int updateFence(String fenceId, GeofenceRequest request, String wkt) {
        return mapper.updateFence(fenceId, request.orgId(), request.fenceName().trim(), request.cityCode(),
                request.fenceType().name(), wkt, request.status().name());
    }

    /** 输入: 围栏编号; 输出: 受影响行数, 采用停用而非物理删除。 */
    public int disableFence(String fenceId) {
        return mapper.disableFence(fenceId);
    }

    /** 输入: 城市代码; 输出: 停车点及当前覆盖车辆数。 */
    public List<ParkingPoint> findParkingPoints(String cityCode) {
        return mapper.findParkingPoints(cityCode).stream().map(this::mapParkingPoint).toList();
    }

    /** 输入: 停车点编号; 输出: 对应停车点。 */
    public Optional<ParkingPoint> findParkingPoint(String pointId) {
        return Optional.ofNullable(mapper.findParkingPoint(pointId)).map(this::mapParkingPoint);
    }

    /** 输入: 停车点编号、请求和操作者; 输出: 无, 新增停车点。 */
    public void insertParkingPoint(String pointId, ParkingPointRequest request, String operatorId) {
        mapper.insertParkingPoint(pointId, request.orgId(), request.pointName().trim(), request.cityCode(),
                request.location().longitude(), request.location().latitude(), request.radiusMeters(),
                request.capacity(), request.status().name(), operatorId);
    }

    /** 输入: 停车点编号和请求; 输出: 受影响行数。 */
    public int updateParkingPoint(String pointId, ParkingPointRequest request) {
        return mapper.updateParkingPoint(pointId, request.orgId(), request.pointName().trim(), request.cityCode(),
                request.location().longitude(), request.location().latitude(), request.radiusMeters(),
                request.capacity(), request.status().name());
    }

    /** 输入: 停车点编号; 输出: 受影响行数, 采用停用而非物理删除。 */
    public int disableParkingPoint(String pointId) {
        return mapper.disableParkingPoint(pointId);
    }

    /** 输入: 城市代码; 输出: 基于最新位置计算的空间违规车辆, 最多 500 条。 */
    public List<GeoViolation> findViolations(String cityCode) {
        return mapper.findViolations(cityCode);
    }

    /** 输入: 组织编号和城市代码; 输出: 组织是否启用且可管理该城市。 */
    public boolean organizationSupportsCity(String orgId, String cityCode) {
        return mapper.organizationSupportsCity(orgId, cityCode);
    }

    private Geofence mapFence(FenceRow row) {
        var polygon = jsonMapper.readValue(row.boundaryJson(), POLYGON_TYPE);
        var boundary = polygon.coordinates().getFirst().stream()
                .map(pair -> new Coordinate(pair.get(0), pair.get(1))).toList();
        return new Geofence(row.fenceId(), row.fenceName(), row.cityCode(), row.fenceType(), row.orgId(),
                row.orgName(), row.status(), boundary, row.areaSquareMeters(), row.updatedAt());
    }

    private ParkingPoint mapParkingPoint(ParkingPointRow row) {
        return new ParkingPoint(row.pointId(), row.pointName(), row.cityCode(), row.orgId(), row.orgName(),
                row.status(), new Coordinate(row.longitude(), row.latitude()), row.radiusMeters(),
                row.capacity(), row.vehicleCount(), row.updatedAt());
    }

    private record GeoJsonPolygon(String type, List<List<List<BigDecimal>>> coordinates) {
    }
}

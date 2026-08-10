package cn.bike.platform.geo;

import cn.bike.platform.geo.GeoModels.Coordinate;
import cn.bike.platform.geo.GeoModels.FacilityStatus;
import cn.bike.platform.geo.GeoModels.FenceType;
import cn.bike.platform.geo.GeoModels.Geofence;
import cn.bike.platform.geo.GeoModels.GeofenceRequest;
import cn.bike.platform.geo.GeoModels.GeoViolation;
import cn.bike.platform.geo.GeoModels.ParkingPoint;
import cn.bike.platform.geo.GeoModels.ParkingPointRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class GeoRepository {

    private static final TypeReference<GeoJsonPolygon> POLYGON_TYPE = new TypeReference<>() {
    };

    private final JdbcClient jdbcClient;
    private final JsonMapper jsonMapper;

    public GeoRepository(JdbcClient jdbcClient, JsonMapper jsonMapper) {
        this.jdbcClient = jdbcClient;
        this.jsonMapper = jsonMapper;
    }

    /** 输入: 城市代码; 输出: 该城市全部围栏。 */
    public List<Geofence> findFences(String cityCode) {
        return jdbcClient.sql("""
                        SELECT f.*, o.org_name, ST_AsGeoJSON(f.boundary) AS boundary_json,
                               round(ST_Area(f.boundary::geography)::numeric, 2) AS area_square_meters
                        FROM geofence f JOIN organization o ON o.org_id = f.org_id
                        WHERE f.city_code = :cityCode AND f.status = 'ACTIVE' ORDER BY f.created_at DESC
                        """)
                .param("cityCode", cityCode).query(this::mapFence).list();
    }

    /** 输入: 围栏编号; 输出: 对应围栏。 */
    public Optional<Geofence> findFence(String fenceId) {
        return jdbcClient.sql("""
                        SELECT f.*, o.org_name, ST_AsGeoJSON(f.boundary) AS boundary_json,
                               round(ST_Area(f.boundary::geography)::numeric, 2) AS area_square_meters
                        FROM geofence f JOIN organization o ON o.org_id = f.org_id
                        WHERE f.fence_id = :fenceId
                        """)
                .param("fenceId", fenceId).query(this::mapFence).optional();
    }

    /** 输入: 围栏编号、请求、WKT 和操作者; 输出: 无, 新增围栏。 */
    public void insertFence(String fenceId, GeofenceRequest request, String wkt, String operatorId) {
        jdbcClient.sql("""
                        INSERT INTO geofence (
                            fence_id, org_id, fence_name, city_code, fence_type, boundary, status, created_by
                        ) VALUES (
                            :fenceId, :orgId, :name, :cityCode, :type,
                            ST_GeomFromText(:wkt, 4326), :status, :operatorId
                        )
                        """)
                .param("fenceId", fenceId).param("orgId", request.orgId())
                .param("name", request.fenceName().trim()).param("cityCode", request.cityCode())
                .param("type", request.fenceType().name()).param("wkt", wkt)
                .param("status", request.status().name()).param("operatorId", operatorId).update();
    }

    /** 输入: 围栏编号、请求和 WKT; 输出: 受影响行数。 */
    public int updateFence(String fenceId, GeofenceRequest request, String wkt) {
        return jdbcClient.sql("""
                        UPDATE geofence SET org_id = :orgId, fence_name = :name, city_code = :cityCode,
                            fence_type = :type, boundary = ST_GeomFromText(:wkt, 4326),
                            status = :status, updated_at = now() WHERE fence_id = :fenceId
                        """)
                .param("fenceId", fenceId).param("orgId", request.orgId())
                .param("name", request.fenceName().trim()).param("cityCode", request.cityCode())
                .param("type", request.fenceType().name()).param("wkt", wkt)
                .param("status", request.status().name()).update();
    }

    /** 输入: 围栏编号; 输出: 受影响行数, 采用停用而非物理删除。 */
    public int disableFence(String fenceId) {
        return jdbcClient.sql("UPDATE geofence SET status = 'DISABLED', updated_at = now() WHERE fence_id = :id")
                .param("id", fenceId).update();
    }

    /** 输入: 城市代码; 输出: 停车点及当前覆盖车辆数。 */
    public List<ParkingPoint> findParkingPoints(String cityCode) {
        return jdbcClient.sql("""
                        SELECT p.*, o.org_name, ST_X(p.location) AS longitude, ST_Y(p.location) AS latitude,
                               count(l.vehicle_id) FILTER (
                                   WHERE ST_DWithin(l.position::geography, p.location::geography, p.radius_meters)
                               ) AS vehicle_count
                        FROM parking_point p
                        JOIN organization o ON o.org_id = p.org_id
                        LEFT JOIN vehicle_latest l ON true
                        WHERE p.city_code = :cityCode AND p.status = 'ACTIVE'
                        GROUP BY p.point_id, o.org_name ORDER BY p.created_at DESC
                        """)
                .param("cityCode", cityCode).query(this::mapParkingPoint).list();
    }

    /** 输入: 停车点编号; 输出: 对应停车点。 */
    public Optional<ParkingPoint> findParkingPoint(String pointId) {
        return jdbcClient.sql("""
                        SELECT p.*, o.org_name, ST_X(p.location) AS longitude, ST_Y(p.location) AS latitude,
                               count(l.vehicle_id) FILTER (
                                   WHERE ST_DWithin(l.position::geography, p.location::geography, p.radius_meters)
                               ) AS vehicle_count
                        FROM parking_point p JOIN organization o ON o.org_id = p.org_id
                        LEFT JOIN vehicle_latest l ON true
                        WHERE p.point_id = :pointId GROUP BY p.point_id, o.org_name
                        """)
                .param("pointId", pointId).query(this::mapParkingPoint).optional();
    }

    /** 输入: 停车点编号、请求和操作者; 输出: 无, 新增停车点。 */
    public void insertParkingPoint(String pointId, ParkingPointRequest request, String operatorId) {
        jdbcClient.sql("""
                        INSERT INTO parking_point (
                            point_id, org_id, point_name, city_code, location,
                            radius_meters, capacity, status, created_by
                        ) VALUES (
                            :pointId, :orgId, :name, :cityCode,
                            ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326),
                            :radius, :capacity, :status, :operatorId
                        )
                        """)
                .param("pointId", pointId).param("orgId", request.orgId())
                .param("name", request.pointName().trim()).param("cityCode", request.cityCode())
                .param("longitude", request.location().longitude()).param("latitude", request.location().latitude())
                .param("radius", request.radiusMeters()).param("capacity", request.capacity())
                .param("status", request.status().name()).param("operatorId", operatorId).update();
    }

    /** 输入: 停车点编号和请求; 输出: 受影响行数。 */
    public int updateParkingPoint(String pointId, ParkingPointRequest request) {
        return jdbcClient.sql("""
                        UPDATE parking_point SET org_id = :orgId, point_name = :name, city_code = :cityCode,
                            location = ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326),
                            radius_meters = :radius, capacity = :capacity, status = :status, updated_at = now()
                        WHERE point_id = :pointId
                        """)
                .param("pointId", pointId).param("orgId", request.orgId())
                .param("name", request.pointName().trim()).param("cityCode", request.cityCode())
                .param("longitude", request.location().longitude()).param("latitude", request.location().latitude())
                .param("radius", request.radiusMeters()).param("capacity", request.capacity())
                .param("status", request.status().name()).update();
    }

    /** 输入: 停车点编号; 输出: 受影响行数, 采用停用而非物理删除。 */
    public int disableParkingPoint(String pointId) {
        return jdbcClient.sql("UPDATE parking_point SET status = 'DISABLED', updated_at = now() WHERE point_id = :id")
                .param("id", pointId).update();
    }

    /** 输入: 城市代码; 输出: 基于最新位置计算的空间违规车辆, 最多 500 条。 */
    public List<GeoViolation> findViolations(String cityCode) {
        return jdbcClient.sql("""
                        WITH city_vehicles AS (
                            SELECT l.* FROM vehicle v
                            JOIN vehicle_latest l ON l.vehicle_id = v.vehicle_id
                            WHERE v.operation_city_code = :cityCode
                        ), violations AS (
                            SELECT cv.vehicle_id, 'OUTSIDE_OPERATION' AS violation_type,
                                   NULL::varchar AS facility_id, NULL::varchar AS facility_name,
                                   cv.longitude, cv.latitude, cv.battery_percent, cv.reported_at
                            FROM city_vehicles cv
                            WHERE EXISTS (
                                SELECT 1 FROM geofence f WHERE f.city_code = :cityCode
                                  AND f.fence_type = 'OPERATION' AND f.status = 'ACTIVE'
                            ) AND NOT EXISTS (
                                SELECT 1 FROM geofence f WHERE f.city_code = :cityCode
                                  AND f.fence_type = 'OPERATION' AND f.status = 'ACTIVE'
                                  AND ST_Covers(f.boundary, cv.position)
                            )
                            UNION ALL
                            SELECT cv.vehicle_id, 'IN_NO_PARK', f.fence_id, f.fence_name,
                                   cv.longitude, cv.latitude, cv.battery_percent, cv.reported_at
                            FROM city_vehicles cv JOIN geofence f
                              ON f.city_code = :cityCode AND f.fence_type = 'NO_PARK' AND f.status = 'ACTIVE'
                             AND ST_Covers(f.boundary, cv.position)
                            WHERE cv.ride_status = 'IDLE'
                            UNION ALL
                            SELECT cv.vehicle_id, 'RIDING_IN_NO_RIDE', f.fence_id, f.fence_name,
                                   cv.longitude, cv.latitude, cv.battery_percent, cv.reported_at
                            FROM city_vehicles cv JOIN geofence f
                              ON f.city_code = :cityCode AND f.fence_type = 'NO_RIDE' AND f.status = 'ACTIVE'
                             AND ST_Covers(f.boundary, cv.position)
                            WHERE cv.ride_status = 'RIDING'
                        )
                        SELECT * FROM violations ORDER BY reported_at DESC LIMIT 500
                        """)
                .param("cityCode", cityCode).query(this::mapViolation).list();
    }

    /** 输入: 组织编号和城市代码; 输出: 组织是否启用且可管理该城市。 */
    public boolean organizationSupportsCity(String orgId, String cityCode) {
        return jdbcClient.sql("""
                        SELECT count(*) > 0 FROM organization
                        WHERE org_id = :orgId AND status = 'ACTIVE'
                          AND (city_code IS NULL OR city_code = :cityCode)
                        """)
                .param("orgId", orgId).param("cityCode", cityCode).query(Boolean.class).single();
    }

    private Geofence mapFence(ResultSet rs, int rowNum) throws SQLException {
        var polygon = jsonMapper.readValue(rs.getString("boundary_json"), POLYGON_TYPE);
        var boundary = polygon.coordinates().getFirst().stream()
                .map(pair -> new Coordinate(pair.get(0), pair.get(1))).toList();
        return new Geofence(rs.getString("fence_id"), rs.getString("fence_name"), rs.getString("city_code"),
                FenceType.valueOf(rs.getString("fence_type")), rs.getString("org_id"), rs.getString("org_name"),
                FacilityStatus.valueOf(rs.getString("status")), boundary, rs.getBigDecimal("area_square_meters"),
                rs.getTimestamp("updated_at").toInstant());
    }

    private ParkingPoint mapParkingPoint(ResultSet rs, int rowNum) throws SQLException {
        return new ParkingPoint(rs.getString("point_id"), rs.getString("point_name"), rs.getString("city_code"),
                rs.getString("org_id"), rs.getString("org_name"), FacilityStatus.valueOf(rs.getString("status")),
                new Coordinate(rs.getBigDecimal("longitude"), rs.getBigDecimal("latitude")),
                rs.getBigDecimal("radius_meters"), rs.getInt("capacity"), rs.getLong("vehicle_count"),
                rs.getTimestamp("updated_at").toInstant());
    }

    private GeoViolation mapViolation(ResultSet rs, int rowNum) throws SQLException {
        return new GeoViolation(rs.getString("vehicle_id"), rs.getString("violation_type"),
                rs.getString("facility_id"), rs.getString("facility_name"), rs.getBigDecimal("longitude"),
                rs.getBigDecimal("latitude"), nullableInteger(rs, "battery_percent"),
                rs.getTimestamp("reported_at").toInstant());
    }

    /** 输入: 结果集与列名; 输出: 保留 SQL NULL 语义的整数值。 */
    private Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        var value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private record GeoJsonPolygon(String type, List<List<List<BigDecimal>>> coordinates) {
    }
}

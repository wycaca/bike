package cn.bike.platform.geo;

import cn.bike.platform.geo.GeoModels.FacilityStatus;
import cn.bike.platform.geo.GeoModels.FenceType;
import cn.bike.platform.geo.GeoModels.GeoViolation;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Mapper
public interface GeoMapper {

    String FENCE_SELECT = """
            SELECT f.fence_id, f.fence_name, f.city_code, f.fence_type, f.org_id, o.org_name,
                   f.status, ST_AsGeoJSON(f.boundary) AS boundary_json,
                   round(ST_Area(f.boundary::geography)::numeric, 2) AS area_square_meters,
                   f.updated_at
            FROM geofence f JOIN organization o ON o.org_id = f.org_id
            """;

    String PARKING_POINT_SELECT = """
            SELECT p.point_id, p.point_name, p.city_code, p.org_id, o.org_name, p.status,
                   ST_X(p.location) AS longitude, ST_Y(p.location) AS latitude,
                   p.radius_meters, p.capacity,
                   count(l.vehicle_id) FILTER (
                       WHERE ST_DWithin(l.position::geography, p.location::geography, p.radius_meters)
                   ) AS vehicle_count,
                   p.updated_at
            FROM parking_point p
            JOIN organization o ON o.org_id = p.org_id
            LEFT JOIN vehicle_latest l ON true
            """;

    @Select(FENCE_SELECT + """
            WHERE f.city_code = #{cityCode} AND f.status = 'ACTIVE'
            ORDER BY f.created_at DESC
            """)
    List<FenceRow> findFences(@Param("cityCode") String cityCode);

    @Select(FENCE_SELECT + " WHERE f.fence_id = #{fenceId}")
    FenceRow findFence(@Param("fenceId") String fenceId);

    @Insert("""
            INSERT INTO geofence (
                fence_id, org_id, fence_name, city_code, fence_type, boundary, status, created_by
            ) VALUES (
                #{fenceId}, #{orgId}, #{name}, #{cityCode}, #{type},
                ST_GeomFromText(#{wkt}, 4326), #{status}, #{operatorId}
            )
            """)
    int insertFence(
            @Param("fenceId") String fenceId,
            @Param("orgId") String orgId,
            @Param("name") String name,
            @Param("cityCode") String cityCode,
            @Param("type") String type,
            @Param("wkt") String wkt,
            @Param("status") String status,
            @Param("operatorId") String operatorId
    );

    @Update("""
            UPDATE geofence SET org_id = #{orgId}, fence_name = #{name}, city_code = #{cityCode},
                fence_type = #{type}, boundary = ST_GeomFromText(#{wkt}, 4326),
                status = #{status}, updated_at = now() WHERE fence_id = #{fenceId}
            """)
    int updateFence(
            @Param("fenceId") String fenceId,
            @Param("orgId") String orgId,
            @Param("name") String name,
            @Param("cityCode") String cityCode,
            @Param("type") String type,
            @Param("wkt") String wkt,
            @Param("status") String status
    );

    @Update("UPDATE geofence SET status = 'DISABLED', updated_at = now() WHERE fence_id = #{id}")
    int disableFence(@Param("id") String fenceId);

    @Select(PARKING_POINT_SELECT + """
            WHERE p.city_code = #{cityCode} AND p.status = 'ACTIVE'
            GROUP BY p.point_id, o.org_name ORDER BY p.created_at DESC
            """)
    List<ParkingPointRow> findParkingPoints(@Param("cityCode") String cityCode);

    @Select(PARKING_POINT_SELECT + """
            WHERE p.point_id = #{pointId}
            GROUP BY p.point_id, o.org_name
            """)
    ParkingPointRow findParkingPoint(@Param("pointId") String pointId);

    @Insert("""
            INSERT INTO parking_point (
                point_id, org_id, point_name, city_code, location,
                radius_meters, capacity, status, created_by
            ) VALUES (
                #{pointId}, #{orgId}, #{name}, #{cityCode},
                ST_SetSRID(ST_MakePoint(#{longitude}, #{latitude}), 4326),
                #{radius}, #{capacity}, #{status}, #{operatorId}
            )
            """)
    int insertParkingPoint(
            @Param("pointId") String pointId,
            @Param("orgId") String orgId,
            @Param("name") String name,
            @Param("cityCode") String cityCode,
            @Param("longitude") BigDecimal longitude,
            @Param("latitude") BigDecimal latitude,
            @Param("radius") BigDecimal radius,
            @Param("capacity") int capacity,
            @Param("status") String status,
            @Param("operatorId") String operatorId
    );

    @Update("""
            UPDATE parking_point SET org_id = #{orgId}, point_name = #{name}, city_code = #{cityCode},
                location = ST_SetSRID(ST_MakePoint(#{longitude}, #{latitude}), 4326),
                radius_meters = #{radius}, capacity = #{capacity}, status = #{status}, updated_at = now()
            WHERE point_id = #{pointId}
            """)
    int updateParkingPoint(
            @Param("pointId") String pointId,
            @Param("orgId") String orgId,
            @Param("name") String name,
            @Param("cityCode") String cityCode,
            @Param("longitude") BigDecimal longitude,
            @Param("latitude") BigDecimal latitude,
            @Param("radius") BigDecimal radius,
            @Param("capacity") int capacity,
            @Param("status") String status
    );

    @Update("UPDATE parking_point SET status = 'DISABLED', updated_at = now() WHERE point_id = #{id}")
    int disableParkingPoint(@Param("id") String pointId);

    @Select("""
            WITH city_vehicles AS (
                SELECT l.* FROM vehicle v
                JOIN vehicle_latest l ON l.vehicle_id = v.vehicle_id
                WHERE v.operation_city_code = #{cityCode}
            ), violations AS (
                SELECT cv.vehicle_id, 'OUTSIDE_OPERATION' AS violation_type,
                       NULL::varchar AS facility_id, NULL::varchar AS facility_name,
                       cv.longitude, cv.latitude, cv.battery_percent, cv.reported_at
                FROM city_vehicles cv
                WHERE EXISTS (
                    SELECT 1 FROM geofence f WHERE f.city_code = #{cityCode}
                      AND f.fence_type = 'OPERATION' AND f.status = 'ACTIVE'
                ) AND NOT EXISTS (
                    SELECT 1 FROM geofence f WHERE f.city_code = #{cityCode}
                      AND f.fence_type = 'OPERATION' AND f.status = 'ACTIVE'
                      AND ST_Covers(f.boundary, cv.position)
                )
                UNION ALL
                SELECT cv.vehicle_id, 'IN_NO_PARK', f.fence_id, f.fence_name,
                       cv.longitude, cv.latitude, cv.battery_percent, cv.reported_at
                FROM city_vehicles cv JOIN geofence f
                  ON f.city_code = #{cityCode} AND f.fence_type = 'NO_PARK' AND f.status = 'ACTIVE'
                 AND ST_Covers(f.boundary, cv.position)
                WHERE cv.ride_status = 'IDLE'
                UNION ALL
                SELECT cv.vehicle_id, 'RIDING_IN_NO_RIDE', f.fence_id, f.fence_name,
                       cv.longitude, cv.latitude, cv.battery_percent, cv.reported_at
                FROM city_vehicles cv JOIN geofence f
                  ON f.city_code = #{cityCode} AND f.fence_type = 'NO_RIDE' AND f.status = 'ACTIVE'
                 AND ST_Covers(f.boundary, cv.position)
                WHERE cv.ride_status = 'RIDING'
            )
            SELECT * FROM violations ORDER BY reported_at DESC LIMIT 500
            """)
    List<GeoViolation> findViolations(@Param("cityCode") String cityCode);

    @Select("""
            SELECT count(*) > 0 FROM organization
            WHERE org_id = #{orgId} AND status = 'ACTIVE'
              AND (city_code IS NULL OR city_code = #{cityCode})
            """)
    boolean organizationSupportsCity(@Param("orgId") String orgId, @Param("cityCode") String cityCode);

    @Insert("""
            INSERT INTO geofence (
                fence_id, org_id, fence_name, city_code, fence_type, boundary, status, created_by
            ) VALUES (
                #{id}, #{orgId}, #{name}, #{cityCode}, #{type},
                ST_GeomFromText(#{wkt}, 4326), 'ACTIVE', 'USR-ADMIN'
            ) ON CONFLICT (fence_id) DO NOTHING
            """)
    int insertMockFence(
            @Param("id") String id,
            @Param("orgId") String orgId,
            @Param("name") String name,
            @Param("cityCode") String cityCode,
            @Param("type") String type,
            @Param("wkt") String wkt
    );

    @Insert("""
            INSERT INTO parking_point (
                point_id, org_id, point_name, city_code, location,
                radius_meters, capacity, status, created_by
            ) VALUES (
                #{id}, #{orgId}, #{name}, #{cityCode},
                ST_SetSRID(ST_MakePoint(#{longitude}, #{latitude}), 4326),
                300, 80, 'ACTIVE', 'USR-ADMIN'
            ) ON CONFLICT (point_id) DO NOTHING
            """)
    int insertMockParkingPoint(
            @Param("id") String id,
            @Param("orgId") String orgId,
            @Param("name") String name,
            @Param("cityCode") String cityCode,
            @Param("longitude") double longitude,
            @Param("latitude") double latitude
    );

    record FenceRow(
            String fenceId,
            String fenceName,
            String cityCode,
            FenceType fenceType,
            String orgId,
            String orgName,
            FacilityStatus status,
            String boundaryJson,
            BigDecimal areaSquareMeters,
            Instant updatedAt
    ) {
    }

    record ParkingPointRow(
            String pointId,
            String pointName,
            String cityCode,
            String orgId,
            String orgName,
            FacilityStatus status,
            BigDecimal longitude,
            BigDecimal latitude,
            BigDecimal radiusMeters,
            int capacity,
            long vehicleCount,
            Instant updatedAt
    ) {
    }
}

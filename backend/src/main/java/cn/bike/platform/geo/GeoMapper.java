package cn.bike.platform.geo;

import cn.bike.platform.geo.GeoModels.FacilityStatus;
import cn.bike.platform.geo.GeoModels.FenceType;
import cn.bike.platform.geo.GeoModels.GeoViolation;
import cn.bike.platform.security.DataPermission;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 地理围栏和运营组织的持久化 Mapper.
 * 空间相交、点面判断等计算交给 PostGIS, 保证索引可用并统一坐标判断口径.
 */
@Mapper
public interface GeoMapper {

    List<FenceRow> findFences(
            @Param("cityCode") String cityCode,
            @Param("permission") DataPermission permission
    );

    FenceRow findFence(
            @Param("fenceId") String fenceId,
            @Param("permission") DataPermission permission
    );

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

    List<ParkingPointRow> findParkingPoints(
            @Param("cityCode") String cityCode,
            @Param("permission") DataPermission permission
    );

    ParkingPointRow findParkingPoint(
            @Param("pointId") String pointId,
            @Param("permission") DataPermission permission
    );

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

    List<GeoViolation> findViolations(
            @Param("cityCode") String cityCode,
            @Param("permission") DataPermission permission
    );

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

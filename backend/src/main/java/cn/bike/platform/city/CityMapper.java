package cn.bike.platform.city;

import cn.bike.platform.city.CityModels.City;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface CityMapper {

    @Select("""
            <script>
            SELECT c.*, o.org_name FROM operation_city c
            JOIN organization o ON o.org_id = c.org_id
            WHERE c.status = 'ACTIVE' AND o.status = 'ACTIVE'
              AND (#{unrestricted} OR c.org_id IN
                <foreach collection="orgIds" item="orgId" open="(" separator="," close=")">#{orgId}</foreach>)
            ORDER BY c.city_name
            </script>
            """)
    List<City> findVisible(
            @Param("unrestricted") boolean unrestricted,
            @Param("orgIds") List<String> orgIds
    );

    @Select("""
            SELECT c.*, o.org_name FROM operation_city c
            JOIN organization o ON o.org_id = c.org_id ORDER BY c.city_name
            """)
    List<City> findAll();

    @Select("""
            SELECT c.*, o.org_name FROM operation_city c
            JOIN organization o ON o.org_id = c.org_id WHERE c.city_code = #{cityCode}
            """)
    City findByCode(@Param("cityCode") String cityCode);

    @Insert("""
            INSERT INTO operation_city (
                city_code, city_name, org_id, center_longitude, center_latitude,
                min_longitude, min_latitude, max_longitude, max_latitude, status
            ) VALUES (
                #{cityCode}, #{cityName}, #{orgId}, #{centerLongitude}, #{centerLatitude},
                #{minLongitude}, #{minLatitude}, #{maxLongitude}, #{maxLatitude}, #{status}
            )
            """)
    int insert(
            @Param("cityCode") String cityCode,
            @Param("cityName") String cityName,
            @Param("orgId") String orgId,
            @Param("centerLongitude") BigDecimal centerLongitude,
            @Param("centerLatitude") BigDecimal centerLatitude,
            @Param("minLongitude") BigDecimal minLongitude,
            @Param("minLatitude") BigDecimal minLatitude,
            @Param("maxLongitude") BigDecimal maxLongitude,
            @Param("maxLatitude") BigDecimal maxLatitude,
            @Param("status") String status
    );

    @Update("""
            UPDATE operation_city SET city_name = #{cityName}, org_id = #{orgId},
                center_longitude = #{centerLongitude}, center_latitude = #{centerLatitude},
                min_longitude = #{minLongitude}, min_latitude = #{minLatitude},
                max_longitude = #{maxLongitude}, max_latitude = #{maxLatitude},
                status = #{status}, updated_at = now()
            WHERE city_code = #{cityCode}
            """)
    int update(
            @Param("cityCode") String cityCode,
            @Param("cityName") String cityName,
            @Param("orgId") String orgId,
            @Param("centerLongitude") BigDecimal centerLongitude,
            @Param("centerLatitude") BigDecimal centerLatitude,
            @Param("minLongitude") BigDecimal minLongitude,
            @Param("minLatitude") BigDecimal minLatitude,
            @Param("maxLongitude") BigDecimal maxLongitude,
            @Param("maxLatitude") BigDecimal maxLatitude,
            @Param("status") String status
    );

    @Insert("""
            INSERT INTO operation_city (
                city_code, city_name, org_id, center_longitude, center_latitude,
                min_longitude, min_latitude, max_longitude, max_latitude, status
            ) VALUES (
                #{cityCode}, #{cityName}, #{orgId}, #{centerLongitude}, #{centerLatitude},
                #{minLongitude}, #{minLatitude}, #{maxLongitude}, #{maxLatitude}, 'ACTIVE'
            ) ON CONFLICT (city_code) DO NOTHING
            """)
    int insertMock(
            @Param("cityCode") String cityCode,
            @Param("cityName") String cityName,
            @Param("orgId") String orgId,
            @Param("centerLongitude") BigDecimal centerLongitude,
            @Param("centerLatitude") BigDecimal centerLatitude,
            @Param("minLongitude") BigDecimal minLongitude,
            @Param("minLatitude") BigDecimal minLatitude,
            @Param("maxLongitude") BigDecimal maxLongitude,
            @Param("maxLatitude") BigDecimal maxLatitude
    );
}

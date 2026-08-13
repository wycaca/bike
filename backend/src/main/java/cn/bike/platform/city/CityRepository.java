package cn.bike.platform.city;

import cn.bike.platform.city.CityModels.City;
import cn.bike.platform.city.CityModels.CityRequest;
import cn.bike.platform.security.DataPermission;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class CityRepository {

    private final CityMapper mapper;

    public CityRepository(CityMapper mapper) {
        this.mapper = mapper;
    }

    /** 输入: 数据权限; 输出: 当前用户可访问的启用城市。 */
    public List<City> findVisible(DataPermission permission) {
        var orgIds = permission.orgIds().isEmpty() ? List.of("__NONE__") : permission.orgIds();
        return mapper.findVisible(permission.unrestricted(), orgIds);
    }

    /** 输入: 无; 输出: 管理员维护使用的全部城市。 */
    public List<City> findAll() {
        return mapper.findAll();
    }

    /** 输入: 城市代码; 输出: 城市配置，不存在时为空。 */
    public Optional<City> findByCode(String cityCode) {
        return Optional.ofNullable(mapper.findByCode(cityCode));
    }

    /** 输入: 城市请求; 输出: 新增记录数。 */
    public int insert(CityRequest request) {
        return mapper.insert(request.cityCode(), request.cityName().trim(), request.orgId(),
                request.centerLongitude(), request.centerLatitude(), request.minLongitude(), request.minLatitude(),
                request.maxLongitude(), request.maxLatitude(), request.status().name());
    }

    /** 输入: 城市代码和请求; 输出: 更新记录数。 */
    public int update(String cityCode, CityRequest request) {
        return mapper.update(cityCode, request.cityName().trim(), request.orgId(),
                request.centerLongitude(), request.centerLatitude(), request.minLongitude(), request.minLatitude(),
                request.maxLongitude(), request.maxLatitude(), request.status().name());
    }
}

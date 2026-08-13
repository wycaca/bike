package cn.bike.platform.city;

import cn.bike.platform.admin.AdminModels.DataScope;
import cn.bike.platform.admin.AdminModels.RecordStatus;
import cn.bike.platform.admin.AdminModels.UserRole;
import cn.bike.platform.admin.AdminRepository;
import cn.bike.platform.city.CityModels.City;
import cn.bike.platform.city.CityModels.CityRequest;
import cn.bike.platform.common.ConflictException;
import cn.bike.platform.common.NotFoundException;
import cn.bike.platform.security.DataPermissionService;
import cn.bike.platform.security.PlatformPrincipal;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CityService {

    private final CityRepository repository;
    private final AdminRepository adminRepository;
    private final DataPermissionService dataPermissionService;

    public CityService(
            CityRepository repository,
            AdminRepository adminRepository,
            DataPermissionService dataPermissionService
    ) {
        this.repository = repository;
        this.adminRepository = adminRepository;
        this.dataPermissionService = dataPermissionService;
    }

    /** 输入: 当前用户; 输出: 用户数据范围内可选择的启用城市。 */
    public List<City> findVisible(PlatformPrincipal principal) {
        return repository.findVisible(dataPermissionService.resolve(principal));
    }

    /** 输入: 全量管理员; 输出: 包含停用项的全部城市配置。 */
    public List<City> findAll(PlatformPrincipal principal) {
        requireGlobalAdmin(principal);
        return repository.findAll();
    }

    /** 输入: 城市配置和全量管理员; 输出: 新增城市。 */
    @Transactional
    public City create(CityRequest request, PlatformPrincipal principal) {
        requireGlobalAdmin(principal);
        validate(request, request.cityCode());
        try {
            repository.insert(request);
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("城市代码或负责组织已被使用");
        }
        return repository.findByCode(request.cityCode()).orElseThrow();
    }

    /** 输入: 城市代码、配置和全量管理员; 输出: 更新后的城市。 */
    @Transactional
    public City update(String cityCode, CityRequest request, PlatformPrincipal principal) {
        requireGlobalAdmin(principal);
        if (!cityCode.equals(request.cityCode())) {
            throw new IllegalArgumentException("城市代码创建后不能修改");
        }
        validate(request, cityCode);
        try {
            if (repository.update(cityCode, request) == 0) {
                throw new NotFoundException("城市不存在: " + cityCode);
            }
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("负责组织已被其他城市使用");
        }
        return repository.findByCode(cityCode).orElseThrow();
    }

    /** 输入: 城市请求和目标代码; 输出: 无，边界或组织不合法时拒绝操作。 */
    private void validate(CityRequest request, String cityCode) {
        if (request.minLongitude().compareTo(request.maxLongitude()) >= 0
                || request.minLatitude().compareTo(request.maxLatitude()) >= 0) {
            throw new IllegalArgumentException("城市地图边界必须满足最小值小于最大值");
        }
        if (request.centerLongitude().compareTo(request.minLongitude()) < 0
                || request.centerLongitude().compareTo(request.maxLongitude()) > 0
                || request.centerLatitude().compareTo(request.minLatitude()) < 0
                || request.centerLatitude().compareTo(request.maxLatitude()) > 0) {
            throw new IllegalArgumentException("城市中心点必须位于地图边界内");
        }
        var organization = adminRepository.findOrganization(request.orgId())
                .orElseThrow(() -> new IllegalArgumentException("负责组织不存在"));
        if (organization.status() != RecordStatus.ACTIVE
                || organization.cityCode() == null || !cityCode.equals(organization.cityCode())) {
            throw new IllegalArgumentException("负责组织未启用或行政区代码与城市不一致");
        }
    }

    /** 输入: 当前用户; 输出: 无，非全量管理员时拒绝操作。 */
    private void requireGlobalAdmin(PlatformPrincipal principal) {
        if (principal.role() != UserRole.ADMIN || principal.dataScope() != DataScope.ALL) {
            throw new AccessDeniedException("只有全量数据管理员可以维护城市");
        }
    }
}

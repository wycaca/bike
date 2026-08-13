package cn.bike.platform.vehicle;

import cn.bike.platform.admin.AdminModels.DataScope;
import cn.bike.platform.admin.AdminModels.RecordStatus;
import cn.bike.platform.admin.AdminModels.UserRole;
import cn.bike.platform.city.CityModels.City;
import cn.bike.platform.city.CityRepository;
import cn.bike.platform.common.ConflictException;
import cn.bike.platform.security.PlatformPrincipal;
import cn.bike.platform.vehicle.FleetModels.VehicleBatchRequest;
import cn.bike.platform.vehicle.FleetModels.VehicleBatchResult;
import cn.bike.platform.vehicle.FleetModels.VehicleBatchSkip;
import cn.bike.platform.vehicle.FleetModels.VehicleCreateRequest;
import cn.bike.platform.vehicle.FleetModels.VehicleCreateResult;
import cn.bike.platform.vehicle.VehicleModels.VehicleAsset;
import jakarta.validation.Validator;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;

@Service
@Profile("!report-worker")
public class FleetService {

    private final VehicleRepository vehicleRepository;
    private final CityRepository cityRepository;
    private final Validator validator;

    public FleetService(VehicleRepository vehicleRepository, CityRepository cityRepository, Validator validator) {
        this.vehicleRepository = vehicleRepository;
        this.cityRepository = cityRepository;
        this.validator = validator;
    }

    /** 输入: 单辆车辆档案和全量管理员; 输出: 新增车辆的关键归属信息。 */
    @Transactional
    public VehicleCreateResult create(VehicleCreateRequest request, PlatformPrincipal principal) {
        requireGlobalAdmin(principal);
        var city = validateBusinessRules(request);
        var asset = toAsset(request, city);
        if (vehicleRepository.insertVehicle(asset) == 0) {
            throw new ConflictException("车辆编号、锁编号或控制器编号已存在");
        }
        return new VehicleCreateResult(asset.vehicleId(), asset.operationCityCode(), asset.orgId());
    }

    /**
     * 输入: 最多 500 辆车辆和全量管理员; 输出: 创建数量及逐行跳过原因。
     *
     * 步骤:
     * 1. 每行先执行 Bean Validation 与城市归属校验，坏数据只影响当前行。
     * 2. 使用数据库唯一约束原子判重，避免并发导入绕过应用层检查。
     * 3. 不包裹整批事务，确保已成功的行不会因后续坏数据回滚。
     */
    public VehicleBatchResult createBatch(VehicleBatchRequest request, PlatformPrincipal principal) {
        requireGlobalAdmin(principal);
        var skipped = new ArrayList<VehicleBatchSkip>();
        var created = 0;
        for (var index = 0; index < request.vehicles().size(); index++) {
            var item = request.vehicles().get(index);
            try {
                var violations = validator.validate(item);
                if (!violations.isEmpty()) {
                    var violation = violations.iterator().next();
                    throw new IllegalArgumentException(violation.getPropertyPath() + ": " + violation.getMessage());
                }
                var city = validateBusinessRules(item);
                if (vehicleRepository.insertVehicle(toAsset(item, city)) == 0) {
                    throw new ConflictException("车辆编号、锁编号或控制器编号重复");
                }
                created++;
            } catch (IllegalArgumentException | ConflictException exception) {
                skipped.add(new VehicleBatchSkip(index + 1, safeVehicleId(item), exception.getMessage()));
            }
        }
        return new VehicleBatchResult(request.vehicles().size(), created, skipped.size(), skipped);
    }

    /** 输入: 车辆档案; 输出: 已启用且与车辆行政区一致的城市。 */
    private City validateBusinessRules(VehicleCreateRequest request) {
        var city = cityRepository.findByCode(request.operationCityCode())
                .orElseThrow(() -> new IllegalArgumentException("运营城市不存在"));
        if (city.status() != RecordStatus.ACTIVE) {
            throw new IllegalArgumentException("运营城市已停用");
        }
        if (!request.operationAreaCode().substring(0, 2).equals(request.operationCityCode().substring(0, 2))) {
            throw new IllegalArgumentException("运营区域与城市不属于同一省级行政区");
        }
        if (request.launchDate().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("投放日期不能晚于今天");
        }
        return city;
    }

    /** 输入: 已验证请求与城市; 输出: 清理空白并绑定城市负责组织的车辆资产。 */
    private VehicleAsset toAsset(VehicleCreateRequest request, City city) {
        return new VehicleAsset(
                request.vehicleId().trim(), request.companyId().trim(), city.orgId(), request.lockId().trim(),
                request.controllerId().trim(), blankToNull(request.plateNumber()), blankToNull(request.filingCode()),
                request.model().trim(), blankToNull(request.batchNo()), city.cityCode(),
                request.operationAreaCode(), request.launchDate(), request.lifecycleStatus());
    }

    /** 输入: 可能为空的文本; 输出: 去除首尾空白后的值，空白转 null。 */
    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 输入: 可能不合法的批量行; 输出: 可安全展示的车辆编号。 */
    private String safeVehicleId(VehicleCreateRequest request) {
        return request == null || request.vehicleId() == null ? "" : request.vehicleId();
    }

    /** 输入: 当前用户; 输出: 无，非全量管理员时拒绝资产写入。 */
    private void requireGlobalAdmin(PlatformPrincipal principal) {
        if (principal.role() != UserRole.ADMIN || principal.dataScope() != DataScope.ALL) {
            throw new AccessDeniedException("只有全量数据管理员可以维护车辆资产");
        }
    }
}

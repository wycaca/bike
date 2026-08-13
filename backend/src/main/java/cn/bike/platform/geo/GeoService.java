package cn.bike.platform.geo;

import cn.bike.platform.common.NotFoundException;
import cn.bike.platform.geo.GeoModels.Coordinate;
import cn.bike.platform.geo.GeoModels.Geofence;
import cn.bike.platform.geo.GeoModels.GeofenceRequest;
import cn.bike.platform.geo.GeoModels.GeoOverview;
import cn.bike.platform.geo.GeoModels.ParkingPoint;
import cn.bike.platform.geo.GeoModels.ParkingPointRequest;
import cn.bike.platform.security.DataPermission;
import cn.bike.platform.security.DataPermissionService;
import cn.bike.platform.security.PlatformPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class GeoService {

    private final GeoRepository repository;
    private final DataPermissionService dataPermissionService;

    public GeoService(GeoRepository repository, DataPermissionService dataPermissionService) {
        this.repository = repository;
        this.dataPermissionService = dataPermissionService;
    }

    /** 输入: 城市代码; 输出: 围栏、停车点和实时违规的空间总览。 */
    public GeoOverview overview(String cityCode, PlatformPrincipal principal) {
        validateCityCode(cityCode);
        var permission = dataPermissionService.resolve(principal);
        return new GeoOverview(repository.findFences(cityCode, permission),
                repository.findParkingPoints(cityCode, permission), repository.findViolations(cityCode, permission));
    }

    /** 输入: 围栏请求和操作者; 输出: 新建围栏。 */
    @Transactional
    public Geofence createFence(GeofenceRequest request, PlatformPrincipal operator) {
        validateBoundary(request.boundary());
        var permission = requireOrganization(request.orgId(), request.cityCode(), operator);
        var id = "FNC-" + UUID.randomUUID().toString().substring(0, 12);
        repository.insertFence(id, request, polygonWkt(request.boundary()), operator.userId());
        return repository.findFence(id, permission).orElseThrow();
    }

    /** 输入: 围栏编号、请求和操作者; 输出: 更新后的围栏。 */
    @Transactional
    public Geofence updateFence(String fenceId, GeofenceRequest request, PlatformPrincipal operator) {
        validateBoundary(request.boundary());
        var permission = requireOrganization(request.orgId(), request.cityCode(), operator);
        requireFence(fenceId, permission);
        if (repository.updateFence(fenceId, request, polygonWkt(request.boundary())) == 0) {
            throw new NotFoundException("围栏不存在: " + fenceId);
        }
        return repository.findFence(fenceId, permission).orElseThrow();
    }

    /** 输入: 围栏编号; 输出: 无, 将围栏停用。 */
    public void disableFence(String fenceId, PlatformPrincipal operator) {
        requireFence(fenceId, dataPermissionService.resolve(operator));
        if (repository.disableFence(fenceId) == 0) {
            throw new NotFoundException("围栏不存在: " + fenceId);
        }
    }

    /** 输入: 停车点请求和操作者; 输出: 新建停车点。 */
    @Transactional
    public ParkingPoint createParkingPoint(ParkingPointRequest request, PlatformPrincipal operator) {
        var permission = requireOrganization(request.orgId(), request.cityCode(), operator);
        var id = "PRK-" + UUID.randomUUID().toString().substring(0, 12);
        repository.insertParkingPoint(id, request, operator.userId());
        return repository.findParkingPoint(id, permission).orElseThrow();
    }

    /** 输入: 停车点编号、请求和操作者; 输出: 更新后的停车点。 */
    @Transactional
    public ParkingPoint updateParkingPoint(
            String pointId,
            ParkingPointRequest request,
            PlatformPrincipal operator
    ) {
        var permission = requireOrganization(request.orgId(), request.cityCode(), operator);
        requireParkingPoint(pointId, permission);
        if (repository.updateParkingPoint(pointId, request) == 0) {
            throw new NotFoundException("停车点不存在: " + pointId);
        }
        return repository.findParkingPoint(pointId, permission).orElseThrow();
    }

    /** 输入: 停车点编号; 输出: 无, 将停车点停用。 */
    public void disableParkingPoint(String pointId, PlatformPrincipal operator) {
        requireParkingPoint(pointId, dataPermissionService.resolve(operator));
        if (repository.disableParkingPoint(pointId) == 0) {
            throw new NotFoundException("停车点不存在: " + pointId);
        }
    }

    /** 输入: 围栏边界; 输出: 无, 不闭合或退化时抛出参数错误。 */
    static void validateBoundary(List<Coordinate> boundary) {
        if (boundary == null || boundary.size() < 4) {
            throw new IllegalArgumentException("围栏至少需要 3 个顶点并闭合");
        }
        var first = boundary.getFirst();
        var last = boundary.getLast();
        if (first.longitude().compareTo(last.longitude()) != 0
                || first.latitude().compareTo(last.latitude()) != 0) {
            throw new IllegalArgumentException("围栏首尾坐标必须闭合");
        }
        var uniquePoints = boundary.subList(0, boundary.size() - 1).stream()
                .map(point -> point.longitude().stripTrailingZeros() + "," + point.latitude().stripTrailingZeros())
                .distinct().count();
        if (uniquePoints < 3) {
            throw new IllegalArgumentException("围栏至少需要 3 个不同顶点");
        }
    }

    /** 输入: 已闭合坐标; 输出: PostGIS Polygon WKT。 */
    static String polygonWkt(List<Coordinate> boundary) {
        return "POLYGON((" + boundary.stream()
                .map(point -> point.longitude().toPlainString() + " " + point.latitude().toPlainString())
                .reduce((first, second) -> first + "," + second).orElseThrow() + "))";
    }

    private void validateCityCode(String cityCode) {
        if (cityCode == null || !cityCode.matches("^[0-9]{6}$")) {
            throw new IllegalArgumentException("cityCode 必须是 6 位行政区代码");
        }
    }

    private DataPermission requireOrganization(String orgId, String cityCode, PlatformPrincipal principal) {
        var permission = dataPermissionService.resolve(principal);
        dataPermissionService.requireOrganization(permission, orgId);
        if (!repository.organizationSupportsCity(orgId, cityCode)) {
            throw new IllegalArgumentException("所属组织未启用或不负责当前城市");
        }
        return permission;
    }

    private void requireFence(String fenceId, DataPermission permission) {
        repository.findFence(fenceId, permission)
                .orElseThrow(() -> new NotFoundException("围栏不存在: " + fenceId));
    }

    private void requireParkingPoint(String pointId, DataPermission permission) {
        repository.findParkingPoint(pointId, permission)
                .orElseThrow(() -> new NotFoundException("停车点不存在: " + pointId));
    }
}

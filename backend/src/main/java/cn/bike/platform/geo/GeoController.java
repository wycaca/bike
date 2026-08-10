package cn.bike.platform.geo;

import cn.bike.platform.common.ApiResponse;
import cn.bike.platform.geo.GeoModels.Geofence;
import cn.bike.platform.geo.GeoModels.GeofenceRequest;
import cn.bike.platform.geo.GeoModels.GeoOverview;
import cn.bike.platform.geo.GeoModels.ParkingPoint;
import cn.bike.platform.geo.GeoModels.ParkingPointRequest;
import cn.bike.platform.security.PlatformPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/geo")
public class GeoController {

    private final GeoService service;

    public GeoController(GeoService service) {
        this.service = service;
    }

    /** 输入: 城市代码; 输出: 空间设施与违规总览。 */
    @GetMapping("/overview")
    public ApiResponse<GeoOverview> overview(@RequestParam String cityCode) {
        return ApiResponse.ok(service.overview(cityCode));
    }

    /** 输入: 围栏请求和当前用户; 输出: 新建围栏。 */
    @PostMapping("/fences")
    public ApiResponse<Geofence> createFence(
            @Valid @RequestBody GeofenceRequest request,
            @AuthenticationPrincipal PlatformPrincipal operator
    ) {
        return ApiResponse.ok(service.createFence(request, operator));
    }

    /** 输入: 围栏编号、请求和当前用户; 输出: 更新后的围栏。 */
    @PutMapping("/fences/{fenceId}")
    public ApiResponse<Geofence> updateFence(
            @PathVariable String fenceId,
            @Valid @RequestBody GeofenceRequest request,
            @AuthenticationPrincipal PlatformPrincipal operator
    ) {
        return ApiResponse.ok(service.updateFence(fenceId, request, operator));
    }

    /** 输入: 围栏编号; 输出: 空成功响应。 */
    @DeleteMapping("/fences/{fenceId}")
    public ApiResponse<Void> disableFence(@PathVariable String fenceId) {
        service.disableFence(fenceId);
        return ApiResponse.ok(null);
    }

    /** 输入: 停车点请求和当前用户; 输出: 新建停车点。 */
    @PostMapping("/parking-points")
    public ApiResponse<ParkingPoint> createParkingPoint(
            @Valid @RequestBody ParkingPointRequest request,
            @AuthenticationPrincipal PlatformPrincipal operator
    ) {
        return ApiResponse.ok(service.createParkingPoint(request, operator));
    }

    /** 输入: 停车点编号、请求和当前用户; 输出: 更新后的停车点。 */
    @PutMapping("/parking-points/{pointId}")
    public ApiResponse<ParkingPoint> updateParkingPoint(
            @PathVariable String pointId,
            @Valid @RequestBody ParkingPointRequest request,
            @AuthenticationPrincipal PlatformPrincipal operator
    ) {
        return ApiResponse.ok(service.updateParkingPoint(pointId, request, operator));
    }

    /** 输入: 停车点编号; 输出: 空成功响应。 */
    @DeleteMapping("/parking-points/{pointId}")
    public ApiResponse<Void> disableParkingPoint(@PathVariable String pointId) {
        service.disableParkingPoint(pointId);
        return ApiResponse.ok(null);
    }
}

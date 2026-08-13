package cn.bike.platform.vehicle;

import cn.bike.platform.common.ApiResponse;
import cn.bike.platform.vehicle.VehicleModels.CoordinateSystem;
import cn.bike.platform.vehicle.VehicleModels.LifecycleStatus;
import cn.bike.platform.vehicle.VehicleModels.MapResult;
import cn.bike.platform.vehicle.VehicleModels.PageData;
import cn.bike.platform.vehicle.VehicleModels.VehicleDetail;
import cn.bike.platform.vehicle.VehicleModels.VehicleListItem;
import org.springframework.format.annotation.DateTimeFormat;
import cn.bike.platform.security.PlatformPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1")
public class VehicleController {

    private final VehicleService vehicleService;

    public VehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @GetMapping("/vehicles")
    public ApiResponse<PageData<VehicleListItem>> findVehicles(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String cityCode,
            @RequestParam(required = false) LifecycleStatus lifecycleStatus,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ApiResponse.ok(vehicleService.findVehicles(
                page, pageSize, keyword, cityCode, lifecycleStatus, principal));
    }

    @GetMapping("/vehicles/{vehicleId}")
    public ApiResponse<VehicleDetail> findVehicle(
            @PathVariable String vehicleId,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ApiResponse.ok(vehicleService.findVehicle(vehicleId, principal));
    }

    @GetMapping("/vehicles/{vehicleId}/trajectory")
    public ApiResponse<TrajectoryResult> findTrajectory(
            @PathVariable String vehicleId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime,
            @RequestParam(defaultValue = "WGS84") CoordinateSystem coordinateSystem,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ApiResponse.ok(vehicleService.findTrajectory(
                vehicleId, startTime, endTime, coordinateSystem, principal));
    }

    @GetMapping("/map/vehicles")
    public ApiResponse<MapResult> findMapVehicles(
            @RequestParam BigDecimal minLongitude,
            @RequestParam BigDecimal minLatitude,
            @RequestParam BigDecimal maxLongitude,
            @RequestParam BigDecimal maxLatitude,
            @RequestParam int zoom,
            @RequestParam(required = false) Boolean online,
            @RequestParam(required = false) LifecycleStatus lifecycleStatus,
            @RequestParam(defaultValue = "WGS84") CoordinateSystem coordinateSystem,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ApiResponse.ok(vehicleService.findMap(
                minLongitude, minLatitude, maxLongitude, maxLatitude,
                zoom, online, lifecycleStatus, coordinateSystem, principal));
    }
}

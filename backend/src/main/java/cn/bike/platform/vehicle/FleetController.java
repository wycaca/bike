package cn.bike.platform.vehicle;

import cn.bike.platform.common.ApiResponse;
import cn.bike.platform.security.PlatformPrincipal;
import cn.bike.platform.vehicle.FleetModels.VehicleBatchRequest;
import cn.bike.platform.vehicle.FleetModels.VehicleBatchResult;
import cn.bike.platform.vehicle.FleetModels.VehicleCreateRequest;
import cn.bike.platform.vehicle.FleetModels.VehicleCreateResult;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@Profile("!report-worker")
@RequestMapping("/api/v1/admin/vehicles")
public class FleetController {

    private final FleetService service;

    public FleetController(FleetService service) {
        this.service = service;
    }

    /** 输入: 单辆车辆档案; 输出: 新建车辆编号和资源地址。 */
    @PostMapping
    public ResponseEntity<ApiResponse<VehicleCreateResult>> create(
            @Valid @RequestBody VehicleCreateRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        var created = service.create(request, principal);
        return ResponseEntity.created(URI.create("/api/v1/vehicles/" + created.vehicleId()))
                .body(ApiResponse.ok(created));
    }

    /** 输入: 最多 500 条车辆档案; 输出: 成功数及逐行跳过原因。 */
    @PostMapping("/batch")
    public ApiResponse<VehicleBatchResult> createBatch(
            @Valid @RequestBody VehicleBatchRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ApiResponse.ok(service.createBatch(request, principal));
    }
}

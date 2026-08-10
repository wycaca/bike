package cn.bike.platform.ops;

import cn.bike.platform.common.ApiResponse;
import cn.bike.platform.ops.OperationsModels.RouteOptimizationRequest;
import cn.bike.platform.ops.OperationsModels.RoutePlan;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ops/routes")
public class OperationsRouteController {

    private final OperationsRouteService service;

    public OperationsRouteController(OperationsRouteService service) {
        this.service = service;
    }

    /** 输入: 待执行任务和可选起点; 输出: 道路距离优化后的任务顺序与折线。 */
    @PostMapping("/optimize")
    public ApiResponse<RoutePlan> optimize(@Valid @RequestBody RouteOptimizationRequest request) {
        return ApiResponse.ok(service.optimize(request));
    }
}

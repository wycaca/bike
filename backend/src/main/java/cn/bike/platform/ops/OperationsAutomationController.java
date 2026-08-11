package cn.bike.platform.ops;

import cn.bike.platform.admin.AdminModels.UserRole;
import cn.bike.platform.common.ApiResponse;
import cn.bike.platform.ops.OperationsModels.AutomationScanResult;
import cn.bike.platform.security.PlatformPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ops/automation")
public class OperationsAutomationController {

    private final OperationsAutomationService service;

    public OperationsAutomationController(OperationsAutomationService service) {
        this.service = service;
    }

    /** 输入: 城市和管理员; 输出: 最新车辆状态规则扫描结果。 */
    @PostMapping("/scan")
    public ApiResponse<AutomationScanResult> scan(
            @RequestParam String cityCode,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        if (principal.role() != UserRole.ADMIN) {
            throw new AccessDeniedException("只有管理员可以触发规则扫描");
        }
        return ApiResponse.ok(service.scanCity(cityCode, principal));
    }
}

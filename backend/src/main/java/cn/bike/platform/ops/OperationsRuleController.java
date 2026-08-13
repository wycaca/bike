package cn.bike.platform.ops;

import cn.bike.platform.common.ApiResponse;
import cn.bike.platform.ops.OperationsModels.TaskRule;
import cn.bike.platform.ops.OperationsModels.TaskRuleRequest;
import cn.bike.platform.security.PlatformPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.net.URI;

@RestController
@RequestMapping("/api/v1/ops/rules")
public class OperationsRuleController {

    private final OperationsRuleService service;

    public OperationsRuleController(OperationsRuleService service) {
        this.service = service;
    }

    /** 输入: 城市; 输出: 自动任务规则。 */
    @GetMapping
    public ApiResponse<List<TaskRule>> findRules(
            @RequestParam String cityCode,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ApiResponse.ok(service.findRules(cityCode, principal));
    }

    /** 输入: 规则配置和管理员; 输出: 新建规则。 */
    @PostMapping
    public ResponseEntity<ApiResponse<TaskRule>> create(
            @Valid @RequestBody TaskRuleRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        var created = service.create(request, principal);
        return ResponseEntity.created(URI.create("/api/v1/ops/rules/" + created.ruleId()))
                .body(ApiResponse.ok(created));
    }

    /** 输入: 规则编号、版本和配置; 输出: 更新后的规则。 */
    @PutMapping("/{ruleId}")
    public ApiResponse<TaskRule> update(
            @PathVariable String ruleId,
            @RequestParam int version,
            @Valid @RequestBody TaskRuleRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ApiResponse.ok(service.update(ruleId, version, request, principal));
    }
}

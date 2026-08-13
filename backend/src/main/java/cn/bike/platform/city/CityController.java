package cn.bike.platform.city;

import cn.bike.platform.city.CityModels.City;
import cn.bike.platform.city.CityModels.CityRequest;
import cn.bike.platform.common.ApiResponse;
import cn.bike.platform.security.PlatformPrincipal;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@Profile("!report-worker")
@RequestMapping("/api/v1")
public class CityController {

    private final CityService service;

    public CityController(CityService service) {
        this.service = service;
    }

    /** 输入: 当前用户; 输出: 数据权限范围内的启用城市。 */
    @GetMapping("/cities")
    public ApiResponse<List<City>> findVisible(@AuthenticationPrincipal PlatformPrincipal principal) {
        return ApiResponse.ok(service.findVisible(principal));
    }

    /** 输入: 全量管理员; 输出: 全部城市配置。 */
    @GetMapping("/admin/cities")
    public ApiResponse<List<City>> findAll(@AuthenticationPrincipal PlatformPrincipal principal) {
        return ApiResponse.ok(service.findAll(principal));
    }

    /** 输入: 城市配置; 输出: 新建城市和资源地址。 */
    @PostMapping("/admin/cities")
    public ResponseEntity<ApiResponse<City>> create(
            @Valid @RequestBody CityRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        var created = service.create(request, principal);
        return ResponseEntity.created(URI.create("/api/v1/admin/cities/" + created.cityCode()))
                .body(ApiResponse.ok(created));
    }

    /** 输入: 城市代码和配置; 输出: 更新后的城市。 */
    @PutMapping("/admin/cities/{cityCode}")
    public ApiResponse<City> update(
            @PathVariable String cityCode,
            @Valid @RequestBody CityRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ApiResponse.ok(service.update(cityCode, request, principal));
    }
}

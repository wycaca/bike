package cn.bike.platform.ops;

import cn.bike.platform.common.ApiResponse;
import cn.bike.platform.ops.OperationsModels.AssigneeOption;
import cn.bike.platform.ops.OperationsModels.AssignmentRequest;
import cn.bike.platform.ops.OperationsModels.BatchCreateResult;
import cn.bike.platform.ops.OperationsModels.BatchCreateTaskRequest;
import cn.bike.platform.ops.OperationsModels.CancellationRequest;
import cn.bike.platform.ops.OperationsModels.CompletionRequest;
import cn.bike.platform.ops.OperationsModels.CreateTaskRequest;
import cn.bike.platform.ops.OperationsModels.ExceptionRequest;
import cn.bike.platform.ops.OperationsModels.ExceptionResolutionRequest;
import cn.bike.platform.ops.OperationsModels.ReviewRequest;
import cn.bike.platform.ops.OperationsModels.TaskDetail;
import cn.bike.platform.ops.OperationsModels.TaskPage;
import cn.bike.platform.ops.OperationsModels.TaskStatus;
import cn.bike.platform.ops.OperationsModels.TaskSummary;
import cn.bike.platform.ops.OperationsModels.TaskType;
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
@RequestMapping("/api/v1/ops/tasks")
public class OperationsController {

    private final OperationsService service;

    public OperationsController(OperationsService service) {
        this.service = service;
    }

    /** 输入: 城市、筛选、分页和当前用户; 输出: 运维任务分页。 */
    @GetMapping
    public ApiResponse<TaskPage> findTasks(
            @RequestParam String cityCode,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) TaskType type,
            @RequestParam(defaultValue = "ALL") String scope,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ApiResponse.ok(service.findTasks(page, pageSize, cityCode, status, type,
                scope, keyword, principal));
    }

    /** 输入: 城市和当前用户; 输出: 运维队列汇总。 */
    @GetMapping("/summary")
    public ApiResponse<TaskSummary> summary(
            @RequestParam String cityCode,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ApiResponse.ok(service.summary(cityCode, principal));
    }

    /** 输入: 城市; 输出: 可指派运维人员。 */
    @GetMapping("/assignees")
    public ApiResponse<List<AssigneeOption>> assignees(
            @RequestParam String cityCode,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ApiResponse.ok(service.assignees(cityCode, principal));
    }

    /** 输入: 任务编号; 输出: 任务详情和事件时间线。 */
    @GetMapping("/{taskId}")
    public ApiResponse<TaskDetail> detail(
            @PathVariable String taskId,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ApiResponse.ok(service.detail(taskId, principal));
    }

    /** 输入: 新任务和当前用户; 输出: 创建后的任务详情。 */
    @PostMapping
    public ResponseEntity<ApiResponse<TaskDetail>> create(
            @Valid @RequestBody CreateTaskRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        var created = service.create(request, principal);
        return ResponseEntity.created(URI.create("/api/v1/ops/tasks/" + created.task().taskId()))
                .body(ApiResponse.ok(created));
    }

    /** 输入: 批量任务模板、车辆列表和当前用户; 输出: 成功任务与逐车跳过原因。 */
    @PostMapping("/batch")
    public ApiResponse<BatchCreateResult> createBatch(
            @Valid @RequestBody BatchCreateTaskRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ApiResponse.ok(service.createBatch(request, principal));
    }

    /** 输入: 待领取任务和当前运维人员; 输出: 抢单后的任务详情。 */
    @PostMapping("/{taskId}/claim")
    public ApiResponse<TaskDetail> claim(
            @PathVariable String taskId,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ApiResponse.ok(service.claim(taskId, principal));
    }

    /** 输入: 任务、目标人员和管理员; 输出: 指派后的任务详情。 */
    @PutMapping("/{taskId}/assignment")
    public ApiResponse<TaskDetail> assign(
            @PathVariable String taskId,
            @Valid @RequestBody AssignmentRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ApiResponse.ok(service.assign(taskId, request, principal));
    }

    /** 输入: 已领取任务和当前运维人员; 输出: 释放后的任务详情。 */
    @PostMapping("/{taskId}/release")
    public ApiResponse<TaskDetail> release(
            @PathVariable String taskId,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ApiResponse.ok(service.release(taskId, principal));
    }

    /** 输入: 已领取任务和当前运维人员; 输出: 开始执行后的任务详情。 */
    @PostMapping("/{taskId}/start")
    public ApiResponse<TaskDetail> start(
            @PathVariable String taskId,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ApiResponse.ok(service.start(taskId, principal));
    }

    /** 输入: 执行中任务、结果和当前运维人员; 输出: 完成后的任务详情。 */
    @PostMapping("/{taskId}/complete")
    public ApiResponse<TaskDetail> complete(
            @PathVariable String taskId,
            @Valid @RequestBody CompletionRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ApiResponse.ok(service.complete(taskId, request, principal));
    }

    /** 输入: 待验收任务和审核结论; 输出: 完成或退回执行的任务详情。 */
    @PostMapping("/{taskId}/review")
    public ApiResponse<TaskDetail> review(
            @PathVariable String taskId,
            @Valid @RequestBody ReviewRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ApiResponse.ok(service.review(taskId, request, principal));
    }

    /** 输入: 作业现场异常和附件; 输出: 进入异常闭环的任务详情。 */
    @PostMapping("/{taskId}/exception")
    public ApiResponse<TaskDetail> reportException(
            @PathVariable String taskId,
            @Valid @RequestBody ExceptionRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ApiResponse.ok(service.reportException(taskId, request, principal));
    }

    /** 输入: 异常任务和管理员处理动作; 输出: 重开或关闭后的任务详情。 */
    @PostMapping("/{taskId}/exception/resolve")
    public ApiResponse<TaskDetail> resolveException(
            @PathVariable String taskId,
            @Valid @RequestBody ExceptionResolutionRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ApiResponse.ok(service.resolveException(taskId, request, principal));
    }

    /** 输入: 未结束任务、取消原因和管理员; 输出: 取消后的任务详情。 */
    @PostMapping("/{taskId}/cancel")
    public ApiResponse<TaskDetail> cancel(
            @PathVariable String taskId,
            @Valid @RequestBody CancellationRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ApiResponse.ok(service.cancel(taskId, request, principal));
    }
}

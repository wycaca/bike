package cn.bike.platform.common;

import cn.bike.platform.admin.AdminController;
import cn.bike.platform.admin.AdminModels.Organization;
import cn.bike.platform.admin.AdminModels.OrganizationRequest;
import cn.bike.platform.admin.AdminModels.PlatformUser;
import cn.bike.platform.admin.AdminModels.UserRequest;
import cn.bike.platform.admin.AdminService;
import cn.bike.platform.geo.GeoController;
import cn.bike.platform.geo.GeoModels.Geofence;
import cn.bike.platform.geo.GeoModels.GeofenceRequest;
import cn.bike.platform.geo.GeoModels.ParkingPoint;
import cn.bike.platform.geo.GeoModels.ParkingPointRequest;
import cn.bike.platform.geo.GeoService;
import cn.bike.platform.integration.yadea.YadeaMockController;
import cn.bike.platform.ops.OperationsAttachmentController;
import cn.bike.platform.ops.OperationsAttachmentService;
import cn.bike.platform.ops.OperationsController;
import cn.bike.platform.ops.OperationsModels.AttachmentUploadResult;
import cn.bike.platform.ops.OperationsModels.CreateTaskRequest;
import cn.bike.platform.ops.OperationsModels.TaskDetail;
import cn.bike.platform.ops.OperationsModels.TaskItem;
import cn.bike.platform.ops.OperationsModels.TaskRule;
import cn.bike.platform.ops.OperationsModels.TaskRuleRequest;
import cn.bike.platform.ops.OperationsRuleController;
import cn.bike.platform.ops.OperationsRuleService;
import cn.bike.platform.ops.OperationsService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RestControllerStatusTest {

    @Test
    void 资源创建接口应返回201和资源地址() throws Exception {
        var organization = mock(Organization.class);
        when(organization.orgId()).thenReturn("ORG-1");
        var adminService = mock(AdminService.class);
        var organizationRequest = mock(OrganizationRequest.class);
        when(adminService.createOrganization(organizationRequest, null)).thenReturn(organization);
        var organizationResponse = new AdminController(adminService).createOrganization(organizationRequest, null);
        assertCreated(organizationResponse, "/api/v1/admin/organizations/ORG-1");

        var user = mock(PlatformUser.class);
        when(user.userId()).thenReturn("USR-1");
        var userRequest = mock(UserRequest.class);
        when(adminService.createUser(userRequest, null)).thenReturn(user);
        assertCreated(new AdminController(adminService).createUser(userRequest, null), "/api/v1/admin/users/USR-1");

        var geoService = mock(GeoService.class);
        var fence = mock(Geofence.class);
        when(fence.fenceId()).thenReturn("FNC-1");
        var fenceRequest = mock(GeofenceRequest.class);
        when(geoService.createFence(fenceRequest, null)).thenReturn(fence);
        assertCreated(new GeoController(geoService).createFence(fenceRequest, null), "/api/v1/geo/fences/FNC-1");

        var point = mock(ParkingPoint.class);
        when(point.pointId()).thenReturn("PRK-1");
        var pointRequest = mock(ParkingPointRequest.class);
        when(geoService.createParkingPoint(pointRequest, null)).thenReturn(point);
        assertCreated(new GeoController(geoService).createParkingPoint(pointRequest, null),
                "/api/v1/geo/parking-points/PRK-1");

        var operationsService = mock(OperationsService.class);
        var detail = mock(TaskDetail.class);
        var task = mock(TaskItem.class);
        when(task.taskId()).thenReturn("TASK-1");
        when(detail.task()).thenReturn(task);
        var taskRequest = mock(CreateTaskRequest.class);
        when(operationsService.create(taskRequest, null)).thenReturn(detail);
        assertCreated(new OperationsController(operationsService).create(taskRequest, null), "/api/v1/ops/tasks/TASK-1");

        var ruleService = mock(OperationsRuleService.class);
        var rule = mock(TaskRule.class);
        when(rule.ruleId()).thenReturn("RULE-1");
        var ruleRequest = mock(TaskRuleRequest.class);
        when(ruleService.create(ruleRequest, null)).thenReturn(rule);
        assertCreated(new OperationsRuleController(ruleService).create(ruleRequest, null), "/api/v1/ops/rules/RULE-1");

        var attachmentService = mock(OperationsAttachmentService.class);
        var attachment = mock(AttachmentUploadResult.class);
        when(attachment.downloadUrl()).thenReturn("/api/v1/ops/attachments/1");
        when(attachmentService.upload("TASK-1", null, null, null)).thenReturn(attachment);
        assertCreated(new OperationsAttachmentController(attachmentService)
                .upload("TASK-1", null, null, null), "/api/v1/ops/attachments/1");
    }

    @Test
    void 雅迪队列接收接口应声明202() throws Exception {
        var method = YadeaMockController.class.getDeclaredMethod("acceptEvent",
                cn.bike.platform.telemetry.TelemetryModels.YadeaCloudEvent.class);

        assertThat(method.getAnnotation(ResponseStatus.class).value()).isEqualTo(HttpStatus.ACCEPTED);
    }

    private void assertCreated(org.springframework.http.ResponseEntity<?> response, String location) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).hasToString(location);
    }
}

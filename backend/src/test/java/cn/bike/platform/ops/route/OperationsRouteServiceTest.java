package cn.bike.platform.ops.route;

import cn.bike.platform.TestDataPermissions;
import cn.bike.platform.admin.AdminModels.DataScope;
import cn.bike.platform.admin.AdminModels.UserRole;
import cn.bike.platform.ops.OperationsModels.RouteCoordinate;
import cn.bike.platform.ops.OperationsModels.RouteOptimizationRequest;
import cn.bike.platform.ops.OperationsModels.TaskItem;
import cn.bike.platform.ops.OperationsModels.TaskPriority;
import cn.bike.platform.ops.OperationsModels.TaskSourceType;
import cn.bike.platform.ops.OperationsModels.TaskStatus;
import cn.bike.platform.ops.OperationsModels.TaskType;
import cn.bike.platform.ops.OperationsRepository;
import cn.bike.platform.security.PlatformPrincipal;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OperationsRouteServiceTest {

    @Test
    void 应按道路矩阵缩短批量任务路线() {
        var repository = mock(OperationsRepository.class);
        var provider = mock(OperationsRouteProvider.class);
        var service = new OperationsRouteService(repository, provider, TestDataPermissions.allService());
        var tasks = List.of(task("A", "116.40"), task("B", "116.41"), task("C", "116.42"));
        var request = new RouteOptimizationRequest(List.of("A", "B", "C"), null, null);
        var points = tasks.stream()
                .map(task -> new RouteCoordinate(task.sourceLongitude(), task.sourceLatitude())).toList();
        long[][] distances = {{0, 10, 2}, {10, 0, 2}, {2, 2, 0}};
        long[][] durations = {{0, 100, 20}, {100, 0, 20}, {20, 20, 0}};
        var matrix = new OperationsRouteProvider.RouteMatrix("AMAP", "GCJ02", null,
                points, distances, durations);
        when(repository.findTasksByIds(request.taskIds())).thenReturn(tasks);
        when(provider.matrix(anyList())).thenReturn(matrix);
        when(provider.polyline(anyList(), org.mockito.ArgumentMatchers.same(matrix))).thenReturn(points);

        var result = service.optimize(request, principal());

        assertThat(result.stops()).extracting(stop -> stop.taskId()).containsExactly("A", "C", "B");
        assertThat(result.totalDistanceMeters()).isEqualTo(4);
        assertThat(result.totalDurationSeconds()).isEqualTo(40);
        assertThat(result.provider()).isEqualTo("AMAP");
    }

    private TaskItem task(String id, String longitude) {
        var now = Instant.parse("2026-08-11T01:00:00Z");
        return new TaskItem(id, "OPS-" + id, TaskType.REBALANCE, TaskStatus.OPEN, TaskPriority.NORMAL,
                TaskSourceType.BATCH, "调度任务" + id, null, "VEHICLE-" + id, "京A0000" + id,
                "110000", "110105", "ORG-BJ", "北京运营中心", null,
                new BigDecimal(longitude), new BigDecimal("39.90"), 50,
                null, null, "USR-ADMIN", "系统管理员", null, null,
                "BATCH-1", "BAT-1", null, 0, now.plusSeconds(3600), null, null,
                null, null, null, null, null, null, 0, now, now);
    }

    private PlatformPrincipal principal() {
        return new PlatformPrincipal("USR-ADMIN", "admin", "encoded", "系统管理员",
                "ORG-HQ", "运营总部", UserRole.ADMIN, DataScope.ALL, true);
    }
}

package cn.bike.platform.ops;

import cn.bike.platform.ops.OperationsModels.RouteCoordinate;
import cn.bike.platform.ops.OperationsModels.RouteOptimizationRequest;
import cn.bike.platform.ops.OperationsModels.RoutePlan;
import cn.bike.platform.ops.OperationsModels.RouteStop;
import cn.bike.platform.ops.OperationsModels.TaskItem;
import cn.bike.platform.ops.OperationsModels.TaskStatus;
import cn.bike.platform.security.PlatformAccessPolicy;
import cn.bike.platform.security.PlatformPrincipal;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

@Service
public class OperationsRouteService {

    private final OperationsRepository repository;
    private final OperationsRouteProvider provider;
    private final PlatformAccessPolicy accessPolicy;

    public OperationsRouteService(
            OperationsRepository repository,
            OperationsRouteProvider provider,
            PlatformAccessPolicy accessPolicy
    ) {
        this.repository = repository;
        this.provider = provider;
        this.accessPolicy = accessPolicy;
    }

    /** 输入: 最多 16 个任务及可选起点; 输出: 按道路距离优化的作业顺序、分段里程和道路折线。 */
    public RoutePlan optimize(RouteOptimizationRequest request, PlatformPrincipal principal) {
        validateCoordinatePair(request);
        if (new HashSet<>(request.taskIds()).size() != request.taskIds().size()) {
            throw new IllegalArgumentException("路线任务编号不能重复");
        }
        var taskById = new HashMap<String, TaskItem>();
        repository.findTasksByIds(request.taskIds()).forEach(task -> taskById.put(task.taskId(), task));
        var tasks = request.taskIds().stream().map(taskId -> {
            var task = taskById.get(taskId);
            if (task == null) {
                throw new IllegalArgumentException("路线中包含不存在的任务: " + taskId);
            }
            accessPolicy.requireTask(principal, task);
            validateTask(task);
            return task;
        }).toList();
        var cityCode = tasks.getFirst().cityCode();
        if (tasks.stream().anyMatch(task -> !cityCode.equals(task.cityCode()))) {
            throw new IllegalArgumentException("一次路线优化只能包含同一城市的任务");
        }

        boolean externalStart = request.startLongitude() != null;
        var points = new ArrayList<RouteCoordinate>();
        if (externalStart) {
            points.add(new RouteCoordinate(request.startLongitude(), request.startLatitude()));
        }
        tasks.forEach(task -> points.add(new RouteCoordinate(task.sourceLongitude(), task.sourceLatitude())));

        var matrix = provider.matrix(points);
        validateMatrix(matrix, points.size());
        var nodeOrder = optimizeOrder(matrix.distances());
        var stops = buildStops(nodeOrder, tasks, matrix, externalStart);
        var orderedPoints = nodeOrder.stream().map(matrix.points()::get).toList();
        long totalDistance = stops.stream().mapToLong(RouteStop::legDistanceMeters).sum();
        long totalDuration = stops.stream().mapToLong(RouteStop::legDurationSeconds).sum();
        return new RoutePlan(matrix.provider(), matrix.coordinateSystem(), matrix.warning(),
                totalDistance, totalDuration, stops, provider.polyline(orderedPoints, matrix));
    }

    /**
     * 输入: 完整道路距离矩阵; 输出: 固定起点的访问顺序。
     * 步骤: 先用最近邻得到稳定初解，再用 2-opt 反转中间区间，直到没有更短的组合。
     */
    List<Integer> optimizeOrder(long[][] distances) {
        int size = distances.length;
        var order = new ArrayList<Integer>();
        var remaining = new HashSet<Integer>();
        order.add(0);
        for (int index = 1; index < size; index++) {
            remaining.add(index);
        }
        while (!remaining.isEmpty()) {
            int current = order.getLast();
            int next = remaining.stream()
                    .min(java.util.Comparator.comparingLong(index -> distances[current][index]))
                    .orElseThrow();
            order.add(next);
            remaining.remove(next);
        }

        boolean improved = true;
        while (improved) {
            improved = false;
            for (int start = 1; start < order.size() - 1; start++) {
                for (int end = start + 1; end < order.size(); end++) {
                    var candidate = new ArrayList<>(order);
                    java.util.Collections.reverse(candidate.subList(start, end + 1));
                    if (routeDistance(candidate, distances) < routeDistance(order, distances)) {
                        order = candidate;
                        improved = true;
                    }
                }
            }
        }
        return List.copyOf(order);
    }

    private List<RouteStop> buildStops(
            List<Integer> order,
            List<TaskItem> tasks,
            OperationsRouteProvider.RouteMatrix matrix,
            boolean externalStart
    ) {
        var stops = new ArrayList<RouteStop>();
        int sequence = 1;
        for (int orderIndex = externalStart ? 1 : 0; orderIndex < order.size(); orderIndex++) {
            int node = order.get(orderIndex);
            int taskIndex = externalStart ? node - 1 : node;
            var task = tasks.get(taskIndex);
            var point = matrix.points().get(node);
            int previousNode = orderIndex == 0 ? node : order.get(orderIndex - 1);
            stops.add(new RouteStop(sequence++, task.taskId(), task.taskNo(), task.vehicleId(), task.title(),
                    point.longitude(), point.latitude(), matrix.distances()[previousNode][node],
                    matrix.durations()[previousNode][node]));
        }
        return List.copyOf(stops);
    }

    private long routeDistance(List<Integer> order, long[][] distances) {
        long total = 0;
        for (int index = 1; index < order.size(); index++) {
            total += distances[order.get(index - 1)][order.get(index)];
        }
        return total;
    }

    private void validateTask(TaskItem task) {
        if (!List.of(TaskStatus.OPEN, TaskStatus.CLAIMED, TaskStatus.IN_PROGRESS).contains(task.status())) {
            throw new IllegalArgumentException("只有待执行或执行中的任务可以参与路线优化: " + task.taskNo());
        }
        if (task.sourceLongitude() == null || task.sourceLatitude() == null) {
            throw new IllegalArgumentException("任务缺少车辆位置: " + task.taskNo());
        }
    }

    private void validateCoordinatePair(RouteOptimizationRequest request) {
        if ((request.startLongitude() == null) != (request.startLatitude() == null)) {
            throw new IllegalArgumentException("路线起点经纬度必须同时填写");
        }
    }

    private void validateMatrix(OperationsRouteProvider.RouteMatrix matrix, int expectedSize) {
        if (matrix.points().size() != expectedSize || matrix.distances().length != expectedSize
                || matrix.durations().length != expectedSize) {
            throw new IllegalStateException("道路距离矩阵维度不正确");
        }
    }
}

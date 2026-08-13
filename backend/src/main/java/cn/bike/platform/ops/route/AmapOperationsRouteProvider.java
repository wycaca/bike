package cn.bike.platform.ops.route;

import cn.bike.platform.ops.OperationsModels.RouteCoordinate;
import cn.bike.platform.vehicle.CoordinateConverter;
import cn.bike.platform.vehicle.VehicleModels.CoordinateSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class AmapOperationsRouteProvider implements OperationsRouteProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(AmapOperationsRouteProvider.class);
    private static final String FALLBACK_WARNING = "高德道路服务暂不可用，当前结果为直线距离修正估算，请勿直接用于里程结算";

    private final RestClient restClient;
    private final String webServiceKey;
    private final String distanceApiUrl;
    private final String drivingApiUrl;
    private final int maxAttempts;
    private final long retryBackoffMs;

    public AmapOperationsRouteProvider(
            @Value("${app.amap.web-service-key:}") String webServiceKey,
            @Value("${app.amap.distance-api-url}") String distanceApiUrl,
            @Value("${app.amap.driving-api-url}") String drivingApiUrl,
            @Value("${app.amap.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${app.amap.read-timeout-ms:4000}") long readTimeoutMs,
            @Value("${app.amap.max-attempts:3}") int maxAttempts,
            @Value("${app.amap.retry-backoff-ms:200}") long retryBackoffMs
    ) {
        this(createRestClient(connectTimeoutMs, readTimeoutMs), webServiceKey, distanceApiUrl,
                drivingApiUrl, maxAttempts, retryBackoffMs);
    }

    /** 输入: 可替换 HTTP 客户端及高德配置; 输出: 便于隔离测试的路线提供器。 */
    AmapOperationsRouteProvider(
            RestClient restClient,
            String webServiceKey,
            String distanceApiUrl,
            String drivingApiUrl,
            int maxAttempts,
            long retryBackoffMs
    ) {
        this.restClient = restClient;
        this.webServiceKey = webServiceKey == null ? "" : webServiceKey.trim();
        this.distanceApiUrl = distanceApiUrl;
        this.drivingApiUrl = drivingApiUrl;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryBackoffMs = Math.max(0, retryBackoffMs);
    }

    /** 输入: WGS84 点位; 输出: 优先使用高德道路距离、失败时使用本地估算的矩阵。 */
    @Override
    public RouteMatrix matrix(List<RouteCoordinate> points) {
        if (webServiceKey.isBlank()) {
            return fallback(points);
        }
        try {
            var gcjPoints = points.stream().map(this::toGcj02).toList();
            int size = gcjPoints.size();
            var distances = new long[size][size];
            var durations = new long[size][size];
            var origins = joinCoordinates(gcjPoints);

            // 高德距离接口一次可提交多个起点，因此按终点循环，构造完整非对称道路矩阵。
            for (int destinationIndex = 0; destinationIndex < size; destinationIndex++) {
                var uri = UriComponentsBuilder.fromUriString(distanceApiUrl)
                        .queryParam("key", webServiceKey)
                        .queryParam("origins", origins)
                        .queryParam("destination", format(gcjPoints.get(destinationIndex)))
                        .queryParam("type", 1)
                        .build().encode().toUri();
                JsonNode response = get(uri);
                requireSuccess(response);
                var results = response.path("results");
                if (!results.isArray() || results.size() != size) {
                    throw new IllegalStateException("高德距离矩阵结果数量不完整");
                }
                for (int originIndex = 0; originIndex < size; originIndex++) {
                    if (originIndex == destinationIndex) {
                        continue;
                    }
                    distances[originIndex][destinationIndex] = parseLong(results.get(originIndex), "distance");
                    durations[originIndex][destinationIndex] = parseLong(results.get(originIndex), "duration");
                }
            }
            return new RouteMatrix("AMAP", "GCJ02", null, gcjPoints, distances, durations);
        } catch (RuntimeException exception) {
            LOGGER.warn("高德道路距离计算失败，使用本地估算: {}", exception.getMessage());
            return fallback(points);
        }
    }

    /** 输入: 已优化的 GCJ02 点位; 输出: 高德驾车道路折线，失败时退回点位连线。 */
    @Override
    public List<RouteCoordinate> polyline(List<RouteCoordinate> orderedPoints, RouteMatrix matrix) {
        if (!"AMAP".equals(matrix.provider()) || orderedPoints.size() < 2) {
            return orderedPoints;
        }
        try {
            var uriBuilder = UriComponentsBuilder.fromUriString(drivingApiUrl)
                    .queryParam("key", webServiceKey)
                    .queryParam("origin", format(orderedPoints.getFirst()))
                    .queryParam("destination", format(orderedPoints.getLast()))
                    .queryParam("extensions", "base");
            if (orderedPoints.size() > 2) {
                uriBuilder.queryParam("waypoints",
                        joinCoordinates(orderedPoints.subList(1, orderedPoints.size() - 1)));
            }
            JsonNode response = get(uriBuilder.build().encode().toUri());
            requireSuccess(response);
            var steps = response.path("route").path("paths").get(0).path("steps");
            var result = new ArrayList<RouteCoordinate>();
            for (var step : steps) {
                for (var coordinate : step.path("polyline").asText("").split(";")) {
                    if (!coordinate.isBlank()) {
                        result.add(parseCoordinate(coordinate));
                    }
                }
            }
            return result.isEmpty() ? orderedPoints : result;
        } catch (RuntimeException exception) {
            LOGGER.warn("高德道路折线计算失败，使用任务点连线: {}", exception.getMessage());
            return orderedPoints;
        }
    }

    /** 输入: 高德 URI; 输出: JSON 响应，仅对网络、限流和 5xx 执行有限重试。 */
    JsonNode get(URI uri) {
        RuntimeException lastException = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                var response = restClient.get().uri(uri).retrieve().body(JsonNode.class);
                if (response == null) {
                    throw new IllegalStateException("高德接口返回空响应");
                }
                return response;
            } catch (RuntimeException exception) {
                lastException = exception;
                if (!retryable(exception) || attempt == maxAttempts) {
                    throw exception;
                }
                LOGGER.warn("高德请求失败，准备第 {}/{} 次尝试: {} {} ({})",
                        attempt + 1, maxAttempts, uri.getHost(), uri.getPath(), exception.getClass().getSimpleName());
                pauseBeforeRetry(attempt);
            }
        }
        throw lastException == null ? new IllegalStateException("高德请求失败") : lastException;
    }

    /** 输入: 请求异常; 输出: 是否属于可安全重试的临时故障。 */
    private boolean retryable(RuntimeException exception) {
        if (exception instanceof ResourceAccessException) {
            return true;
        }
        if (exception instanceof RestClientResponseException responseException) {
            return responseException.getStatusCode().value() == 429
                    || responseException.getStatusCode().is5xxServerError();
        }
        return false;
    }

    /** 输入: 已失败次数; 输出: 无，按线性退避短暂等待并保留线程中断状态。 */
    private void pauseBeforeRetry(int failedAttempts) {
        try {
            Thread.sleep(retryBackoffMs * failedAttempts);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("高德请求重试被中断", exception);
        }
    }

    /** 输入: 连接和读取超时毫秒数; 输出: 使用 JDK HttpClient 的 RestClient。 */
    private static RestClient createRestClient(long connectTimeoutMs, long readTimeoutMs) {
        var httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1, connectTimeoutMs)))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(Math.max(1, readTimeoutMs)));
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    private void requireSuccess(JsonNode response) {
        if (!"1".equals(response.path("status").asText())) {
            throw new IllegalStateException("高德接口错误: " + response.path("info").asText("unknown"));
        }
    }

    private long parseLong(JsonNode node, String field) {
        var value = node.path(field).asText();
        if (value.isBlank()) {
            throw new IllegalStateException("高德接口缺少字段: " + field);
        }
        return Long.parseLong(value);
    }

    private RouteCoordinate toGcj02(RouteCoordinate point) {
        var converted = CoordinateConverter.convert(point.longitude(), point.latitude(),
                CoordinateSystem.WGS84, CoordinateSystem.GCJ02);
        return new RouteCoordinate(converted.longitude(), converted.latitude());
    }

    private RouteMatrix fallback(List<RouteCoordinate> points) {
        int size = points.size();
        var distances = new long[size][size];
        var durations = new long[size][size];
        for (int from = 0; from < size; from++) {
            for (int to = 0; to < size; to++) {
                if (from == to) {
                    continue;
                }
                long distance = Math.round(haversine(points.get(from), points.get(to)) * 1.3);
                distances[from][to] = distance;
                durations[from][to] = Math.max(1, Math.round(distance / 6.94));
            }
        }
        return new RouteMatrix("LOCAL_ESTIMATE", "WGS84", FALLBACK_WARNING,
                List.copyOf(points), distances, durations);
    }

    private double haversine(RouteCoordinate from, RouteCoordinate to) {
        double latitude1 = Math.toRadians(from.latitude().doubleValue());
        double latitude2 = Math.toRadians(to.latitude().doubleValue());
        double latitudeDelta = latitude2 - latitude1;
        double longitudeDelta = Math.toRadians(to.longitude().doubleValue() - from.longitude().doubleValue());
        double a = Math.sin(latitudeDelta / 2) * Math.sin(latitudeDelta / 2)
                + Math.cos(latitude1) * Math.cos(latitude2)
                * Math.sin(longitudeDelta / 2) * Math.sin(longitudeDelta / 2);
        return 6_371_000 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private String joinCoordinates(List<RouteCoordinate> points) {
        return points.stream().map(this::format).collect(java.util.stream.Collectors.joining("|"));
    }

    private String format(RouteCoordinate point) {
        return point.longitude().toPlainString() + "," + point.latitude().toPlainString();
    }

    private RouteCoordinate parseCoordinate(String value) {
        var parts = value.split(",");
        if (parts.length != 2) {
            throw new IllegalArgumentException("高德路线坐标格式错误");
        }
        return new RouteCoordinate(new BigDecimal(parts[0]), new BigDecimal(parts[1]));
    }
}

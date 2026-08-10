package cn.bike.platform.ops;

import cn.bike.platform.ops.OperationsModels.RouteCoordinate;

import java.util.List;

/**
 * 道路距离提供方。排序服务只依赖该接口，测试和降级策略不需要访问外部地图。
 */
public interface OperationsRouteProvider {

    /** 输入: WGS84 起点及任务点; 输出: 同一坐标系下的全量距离和时长矩阵。 */
    RouteMatrix matrix(List<RouteCoordinate> points);

    /** 输入: 已排序的路线坐标; 输出: 可直接绘制的道路折线。 */
    List<RouteCoordinate> polyline(List<RouteCoordinate> orderedPoints, RouteMatrix matrix);

    record RouteMatrix(
            String provider,
            String coordinateSystem,
            String warning,
            List<RouteCoordinate> points,
            long[][] distances,
            long[][] durations
    ) {
    }
}

package cn.bike.platform.vehicle;

import cn.bike.platform.vehicle.VehicleModels.TrajectoryPoint;

import java.util.List;

public record TrajectoryResult(
        List<TrajectoryPoint> points,
        boolean truncated,
        String coordinateSystem
) {
}

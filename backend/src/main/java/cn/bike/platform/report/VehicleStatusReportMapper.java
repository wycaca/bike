package cn.bike.platform.report;

import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

/** 车辆状态导出专用 Mapper，只由独立报表 Worker 的只读连接池加载。 */
public interface VehicleStatusReportMapper {

    /** 输入: 城市代码; 输出: 按车辆编号排序的状态明细。 */
    List<VehicleStatusRow> findRows(@Param("cityCode") String cityCode);

    record VehicleStatusRow(
            String vehicleId,
            String plateNumber,
            String model,
            String cityCode,
            String areaCode,
            String lifecycleStatus,
            Boolean online,
            Integer batteryPercent,
            String controllerStatus,
            Instant reportedAt
    ) {
    }
}

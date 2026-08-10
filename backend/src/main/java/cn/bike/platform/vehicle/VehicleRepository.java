package cn.bike.platform.vehicle;

import cn.bike.platform.telemetry.TelemetryModels.YadeaCloudEvent;
import cn.bike.platform.vehicle.VehicleModels.ControllerStatus;
import cn.bike.platform.vehicle.VehicleModels.LatestState;
import cn.bike.platform.vehicle.VehicleModels.LifecycleStatus;
import cn.bike.platform.vehicle.VehicleModels.LockStatus;
import cn.bike.platform.vehicle.VehicleModels.MapMarker;
import cn.bike.platform.vehicle.VehicleModels.PageData;
import cn.bike.platform.vehicle.VehicleModels.RideStatus;
import cn.bike.platform.vehicle.VehicleModels.TrajectoryPoint;
import cn.bike.platform.vehicle.VehicleModels.VehicleAsset;
import cn.bike.platform.vehicle.VehicleModels.VehicleDetail;
import cn.bike.platform.vehicle.VehicleModels.VehicleListItem;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class VehicleRepository {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final JdbcClient jdbcClient;
    private final JsonMapper jsonMapper;

    public VehicleRepository(JdbcClient jdbcClient, JsonMapper jsonMapper) {
        this.jdbcClient = jdbcClient;
        this.jsonMapper = jsonMapper;
    }

    /**
     * 保存模拟车辆档案. 使用幂等更新, 便于开发环境重复载入同一份 JSON.
     */
    public void upsertVehicle(VehicleAsset asset) {
        jdbcClient.sql("""
                        INSERT INTO vehicle (
                            vehicle_id, company_id, lock_id, controller_id, plate_number, filing_code,
                            model, batch_no, operation_city_code, operation_area_code, launch_date,
                            lifecycle_status
                        ) VALUES (
                            :vehicleId, :companyId, :lockId, :controllerId, :plateNumber, :filingCode,
                            :model, :batchNo, :cityCode, :areaCode, :launchDate, :status
                        )
                        ON CONFLICT (vehicle_id) DO UPDATE SET
                            plate_number = EXCLUDED.plate_number,
                            filing_code = EXCLUDED.filing_code,
                            model = EXCLUDED.model,
                            batch_no = EXCLUDED.batch_no,
                            operation_city_code = EXCLUDED.operation_city_code,
                            operation_area_code = EXCLUDED.operation_area_code,
                            lifecycle_status = EXCLUDED.lifecycle_status,
                            updated_at = now()
                        """)
                .param("vehicleId", asset.vehicleId())
                .param("companyId", asset.companyId())
                .param("lockId", asset.lockId())
                .param("controllerId", asset.controllerId())
                .param("plateNumber", asset.plateNumber(), Types.VARCHAR)
                .param("filingCode", asset.filingCode(), Types.VARCHAR)
                .param("model", asset.model())
                .param("batchNo", asset.batchNo(), Types.VARCHAR)
                .param("cityCode", asset.operationCityCode())
                .param("areaCode", asset.operationAreaCode())
                .param("launchDate", asset.launchDate())
                .param("status", asset.lifecycleStatus().name())
                .update();
    }

    /**
     * 同时写入历史点和最新位置投影. 只允许新时间覆盖旧状态, 防止 Kafka 重试或乱序消息回退车辆状态.
     *
     * @return 最新状态是否被本次事件更新.
     */
    @Transactional
    public boolean saveTelemetry(YadeaCloudEvent event, String rawPayload, String faultCodesJson) {
        var location = event.location();
        var state = event.state();

        jdbcClient.sql("""
                        INSERT INTO vehicle_position (
                            vehicle_id, reported_at, longitude, latitude, position, accuracy_meters,
                            speed_kmh, direction_degrees, satellite_count, battery_percent,
                            remaining_range_km, lock_status, ride_status, controller_status,
                            signal_strength, fault_codes, source, raw_payload
                        ) VALUES (
                            :vehicleId, :reportedAt, :longitude, :latitude,
                            ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326), :accuracyMeters,
                            :speedKmh, :directionDegrees, :satelliteCount, :batteryPercent,
                            :remainingRangeKm, :lockStatus, :rideStatus, :controllerStatus,
                            :signalStrength, CAST(:faultCodes AS jsonb), 'YADEA_CLOUD_MOCK',
                            CAST(:rawPayload AS jsonb)
                        )
                        ON CONFLICT (vehicle_id, reported_at) DO NOTHING
                        """)
                .param("vehicleId", event.vehicleId())
                .param("reportedAt", Timestamp.from(event.occurredAt()))
                .param("longitude", location.longitude())
                .param("latitude", location.latitude())
                .param("accuracyMeters", location.accuracyMeters(), Types.NUMERIC)
                .param("speedKmh", location.speedKmh(), Types.NUMERIC)
                .param("directionDegrees", location.directionDegrees(), Types.SMALLINT)
                .param("satelliteCount", location.satelliteCount(), Types.SMALLINT)
                .param("batteryPercent", state.batteryPercent(), Types.SMALLINT)
                .param("remainingRangeKm", state.remainingRangeKm(), Types.NUMERIC)
                .param("lockStatus", state.lockStatus().name())
                .param("rideStatus", state.rideStatus().name())
                .param("controllerStatus", state.controllerStatus().name())
                .param("signalStrength", state.signalStrength(), Types.SMALLINT)
                .param("faultCodes", faultCodesJson)
                .param("rawPayload", rawPayload)
                .update();

        var latestUpdated = jdbcClient.sql("""
                        INSERT INTO vehicle_latest (
                            vehicle_id, reported_at, longitude, latitude, position, accuracy_meters,
                            speed_kmh, direction_degrees, satellite_count, battery_percent,
                            remaining_range_km, lock_status, ride_status, controller_status,
                            online, signal_strength, fault_codes
                        ) VALUES (
                            :vehicleId, :reportedAt, :longitude, :latitude,
                            ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326), :accuracyMeters,
                            :speedKmh, :directionDegrees, :satelliteCount, :batteryPercent,
                            :remainingRangeKm, :lockStatus, :rideStatus, :controllerStatus,
                            :online, :signalStrength, CAST(:faultCodes AS jsonb)
                        )
                        ON CONFLICT (vehicle_id) DO UPDATE SET
                            reported_at = EXCLUDED.reported_at,
                            longitude = EXCLUDED.longitude,
                            latitude = EXCLUDED.latitude,
                            position = EXCLUDED.position,
                            accuracy_meters = EXCLUDED.accuracy_meters,
                            speed_kmh = EXCLUDED.speed_kmh,
                            direction_degrees = EXCLUDED.direction_degrees,
                            satellite_count = EXCLUDED.satellite_count,
                            battery_percent = EXCLUDED.battery_percent,
                            remaining_range_km = EXCLUDED.remaining_range_km,
                            lock_status = EXCLUDED.lock_status,
                            ride_status = EXCLUDED.ride_status,
                            controller_status = EXCLUDED.controller_status,
                            online = EXCLUDED.online,
                            signal_strength = EXCLUDED.signal_strength,
                            fault_codes = EXCLUDED.fault_codes,
                            updated_at = now()
                        WHERE EXCLUDED.reported_at >= vehicle_latest.reported_at
                        """)
                .param("vehicleId", event.vehicleId())
                .param("reportedAt", Timestamp.from(event.occurredAt()))
                .param("longitude", location.longitude())
                .param("latitude", location.latitude())
                .param("accuracyMeters", location.accuracyMeters(), Types.NUMERIC)
                .param("speedKmh", location.speedKmh(), Types.NUMERIC)
                .param("directionDegrees", location.directionDegrees(), Types.SMALLINT)
                .param("satelliteCount", location.satelliteCount(), Types.SMALLINT)
                .param("batteryPercent", state.batteryPercent(), Types.SMALLINT)
                .param("remainingRangeKm", state.remainingRangeKm(), Types.NUMERIC)
                .param("lockStatus", state.lockStatus().name())
                .param("rideStatus", state.rideStatus().name())
                .param("controllerStatus", state.controllerStatus().name())
                .param("online", state.online())
                .param("signalStrength", state.signalStrength(), Types.SMALLINT)
                .param("faultCodes", faultCodesJson)
                .update();
        return latestUpdated > 0;
    }

    public PageData<VehicleListItem> findVehicles(
            int page,
            int pageSize,
            String keyword,
            String cityCode,
            LifecycleStatus lifecycleStatus
    ) {
        var where = new StringBuilder(" WHERE 1 = 1");
        var parameters = new LinkedHashMap<String, Object>();
        appendVehicleFilters(where, parameters, keyword, cityCode, lifecycleStatus);

        var total = bind(jdbcClient.sql("SELECT count(*) FROM vehicle v" + where), parameters)
                .query(Long.class)
                .single();

        var sql = """
                SELECT v.*, l.reported_at, l.longitude, l.latitude, l.accuracy_meters,
                       l.speed_kmh, l.direction_degrees, l.satellite_count, l.battery_percent,
                       l.remaining_range_km, l.lock_status, l.ride_status, l.controller_status,
                       l.online, l.signal_strength, l.fault_codes
                FROM vehicle v
                LEFT JOIN vehicle_latest l ON l.vehicle_id = v.vehicle_id
                """ + where + " ORDER BY v.vehicle_id LIMIT :limit OFFSET :offset";
        parameters.put("limit", pageSize);
        parameters.put("offset", (page - 1) * pageSize);
        var items = bind(jdbcClient.sql(sql), parameters)
                .query((resultSet, rowNumber) -> mapVehicleListItem(resultSet))
                .list();
        return new PageData<>(items, total, page, pageSize);
    }

    public Optional<VehicleDetail> findVehicle(String vehicleId) {
        return jdbcClient.sql("""
                        SELECT v.*, l.reported_at, l.longitude, l.latitude, l.accuracy_meters,
                               l.speed_kmh, l.direction_degrees, l.satellite_count, l.battery_percent,
                               l.remaining_range_km, l.lock_status, l.ride_status, l.controller_status,
                               l.online, l.signal_strength, l.fault_codes
                        FROM vehicle v
                        LEFT JOIN vehicle_latest l ON l.vehicle_id = v.vehicle_id
                        WHERE v.vehicle_id = :vehicleId
                        """)
                .param("vehicleId", vehicleId)
                .query((resultSet, rowNumber) -> new VehicleDetail(
                        mapVehicleAsset(resultSet), mapLatestState(resultSet)))
                .optional();
    }

    public List<TrajectoryPoint> findTrajectory(String vehicleId, Instant startTime, Instant endTime, int limit) {
        return jdbcClient.sql("""
                        SELECT reported_at, longitude, latitude, accuracy_meters, speed_kmh,
                               direction_degrees, battery_percent, lock_status, ride_status
                        FROM vehicle_position
                        WHERE vehicle_id = :vehicleId
                          AND reported_at >= :startTime
                          AND reported_at <= :endTime
                        ORDER BY reported_at
                        LIMIT :limit
                        """)
                .param("vehicleId", vehicleId)
                .param("startTime", Timestamp.from(startTime))
                .param("endTime", Timestamp.from(endTime))
                .param("limit", limit)
                .query((resultSet, rowNumber) -> new TrajectoryPoint(
                        resultSet.getTimestamp("reported_at").toInstant(),
                        resultSet.getBigDecimal("longitude"),
                        resultSet.getBigDecimal("latitude"),
                        resultSet.getBigDecimal("accuracy_meters"),
                        resultSet.getBigDecimal("speed_kmh"),
                        nullableInteger(resultSet, "direction_degrees"),
                        nullableInteger(resultSet, "battery_percent"),
                        LockStatus.valueOf(resultSet.getString("lock_status")),
                        RideStatus.valueOf(resultSet.getString("ride_status")),
                        "WGS84"))
                .list();
    }

    public List<MapMarker> findMapVehicles(
            BigDecimal minLongitude,
            BigDecimal minLatitude,
            BigDecimal maxLongitude,
            BigDecimal maxLatitude,
            Boolean online,
            LifecycleStatus lifecycleStatus,
            int limit
    ) {
        var filters = buildMapFilters(online, lifecycleStatus);
        var sql = """
                SELECT v.vehicle_id, v.lifecycle_status, l.reported_at, l.longitude, l.latitude,
                       l.accuracy_meters, l.speed_kmh, l.direction_degrees, l.satellite_count,
                       l.battery_percent, l.remaining_range_km, l.lock_status, l.ride_status,
                       l.controller_status, l.online, l.signal_strength, l.fault_codes
                FROM vehicle_latest l
                JOIN vehicle v ON v.vehicle_id = l.vehicle_id
                WHERE l.position && ST_MakeEnvelope(:minLongitude, :minLatitude,
                                                     :maxLongitude, :maxLatitude, 4326)
                """ + filters.sql() + " ORDER BY l.vehicle_id LIMIT :limit";
        var statement = bindMapParameters(jdbcClient.sql(sql), filters.parameters(),
                minLongitude, minLatitude, maxLongitude, maxLatitude)
                .param("limit", limit);
        return statement.query((resultSet, rowNumber) -> {
            var vehicleId = resultSet.getString("vehicle_id");
            var latest = mapLatestState(resultSet);
            return new MapMarker(
                    "VEHICLE", vehicleId, vehicleId,
                    latest.longitude(), latest.latitude(), 1,
                    latest.batteryPercent() != null && latest.batteryPercent() < 20 ? 1 : 0,
                    latest.faultCodes().isEmpty() ? 0 : 1,
                    latest.batteryPercent(),
                    LifecycleStatus.valueOf(resultSet.getString("lifecycle_status")),
                    latest);
        }).list();
    }

    public List<MapMarker> findMapClusters(
            BigDecimal minLongitude,
            BigDecimal minLatitude,
            BigDecimal maxLongitude,
            BigDecimal maxLatitude,
            Boolean online,
            LifecycleStatus lifecycleStatus,
            BigDecimal gridSize,
            int limit
    ) {
        var filters = buildMapFilters(online, lifecycleStatus);
        var gridExpression = "floor((l.longitude + 180) / :gridSize), floor((l.latitude + 90) / :gridSize)";
        var sql = """
                SELECT avg(l.longitude) AS longitude, avg(l.latitude) AS latitude,
                       count(*) AS vehicle_count,
                       count(*) FILTER (WHERE l.battery_percent < 20) AS low_battery_count,
                       count(*) FILTER (WHERE jsonb_array_length(l.fault_codes) > 0) AS fault_count
                FROM vehicle_latest l
                JOIN vehicle v ON v.vehicle_id = l.vehicle_id
                WHERE l.position && ST_MakeEnvelope(:minLongitude, :minLatitude,
                                                     :maxLongitude, :maxLatitude, 4326)
                """ + filters.sql() + " GROUP BY " + gridExpression
                + " ORDER BY count(*) DESC LIMIT :limit";
        var statement = bindMapParameters(jdbcClient.sql(sql), filters.parameters(),
                minLongitude, minLatitude, maxLongitude, maxLatitude)
                .param("gridSize", gridSize)
                .param("limit", limit);
        return statement.query((resultSet, rowNumber) -> new MapMarker(
                "CLUSTER", "cluster-" + rowNumber, null,
                resultSet.getBigDecimal("longitude"),
                resultSet.getBigDecimal("latitude"),
                resultSet.getLong("vehicle_count"),
                resultSet.getLong("low_battery_count"),
                resultSet.getLong("fault_count"),
                null, null, null)).list();
    }

    private void appendVehicleFilters(
            StringBuilder where,
            Map<String, Object> parameters,
            String keyword,
            String cityCode,
            LifecycleStatus lifecycleStatus
    ) {
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (v.vehicle_id ILIKE :keyword OR v.plate_number ILIKE :keyword OR v.lock_id ILIKE :keyword)");
            parameters.put("keyword", "%" + keyword.trim() + "%");
        }
        if (cityCode != null && !cityCode.isBlank()) {
            where.append(" AND v.operation_city_code = :cityCode");
            parameters.put("cityCode", cityCode);
        }
        if (lifecycleStatus != null) {
            where.append(" AND v.lifecycle_status = :lifecycleStatus");
            parameters.put("lifecycleStatus", lifecycleStatus.name());
        }
    }

    private QueryFilters buildMapFilters(Boolean online, LifecycleStatus lifecycleStatus) {
        var sql = new StringBuilder();
        var parameters = new LinkedHashMap<String, Object>();
        if (online != null) {
            sql.append(" AND l.online = :online");
            parameters.put("online", online);
        }
        if (lifecycleStatus != null) {
            sql.append(" AND v.lifecycle_status = :lifecycleStatus");
            parameters.put("lifecycleStatus", lifecycleStatus.name());
        }
        return new QueryFilters(sql.toString(), parameters);
    }

    private JdbcClient.StatementSpec bindMapParameters(
            JdbcClient.StatementSpec statement,
            Map<String, Object> parameters,
            BigDecimal minLongitude,
            BigDecimal minLatitude,
            BigDecimal maxLongitude,
            BigDecimal maxLatitude
    ) {
        statement.param("minLongitude", minLongitude)
                .param("minLatitude", minLatitude)
                .param("maxLongitude", maxLongitude)
                .param("maxLatitude", maxLatitude);
        return bind(statement, parameters);
    }

    private JdbcClient.StatementSpec bind(JdbcClient.StatementSpec statement, Map<String, Object> parameters) {
        parameters.forEach(statement::param);
        return statement;
    }

    private VehicleListItem mapVehicleListItem(ResultSet resultSet) throws SQLException {
        return new VehicleListItem(
                resultSet.getString("vehicle_id"),
                resultSet.getString("plate_number"),
                resultSet.getString("filing_code"),
                resultSet.getString("model"),
                resultSet.getString("operation_city_code"),
                resultSet.getString("operation_area_code"),
                LifecycleStatus.valueOf(resultSet.getString("lifecycle_status")),
                mapLatestState(resultSet));
    }

    private VehicleAsset mapVehicleAsset(ResultSet resultSet) throws SQLException {
        return new VehicleAsset(
                resultSet.getString("vehicle_id"),
                resultSet.getString("company_id"),
                resultSet.getString("lock_id"),
                resultSet.getString("controller_id"),
                resultSet.getString("plate_number"),
                resultSet.getString("filing_code"),
                resultSet.getString("model"),
                resultSet.getString("batch_no"),
                resultSet.getString("operation_city_code"),
                resultSet.getString("operation_area_code"),
                resultSet.getObject("launch_date", LocalDate.class),
                LifecycleStatus.valueOf(resultSet.getString("lifecycle_status")));
    }

    private LatestState mapLatestState(ResultSet resultSet) throws SQLException {
        var reportedAt = resultSet.getTimestamp("reported_at");
        if (reportedAt == null) {
            return null;
        }
        return new LatestState(
                reportedAt.toInstant(),
                resultSet.getBigDecimal("longitude"),
                resultSet.getBigDecimal("latitude"),
                resultSet.getBigDecimal("accuracy_meters"),
                resultSet.getBigDecimal("speed_kmh"),
                nullableInteger(resultSet, "direction_degrees"),
                nullableInteger(resultSet, "satellite_count"),
                nullableInteger(resultSet, "battery_percent"),
                resultSet.getBigDecimal("remaining_range_km"),
                LockStatus.valueOf(resultSet.getString("lock_status")),
                RideStatus.valueOf(resultSet.getString("ride_status")),
                ControllerStatus.valueOf(resultSet.getString("controller_status")),
                resultSet.getBoolean("online"),
                nullableInteger(resultSet, "signal_strength"),
                parseFaultCodes(resultSet.getString("fault_codes")),
                "WGS84");
    }

    private List<String> parseFaultCodes(String json) {
        if (json == null) {
            return List.of();
        }
        return jsonMapper.readValue(json, STRING_LIST_TYPE);
    }

    private Integer nullableInteger(ResultSet resultSet, String column) throws SQLException {
        var value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }

    private record QueryFilters(String sql, Map<String, Object> parameters) {
    }
}

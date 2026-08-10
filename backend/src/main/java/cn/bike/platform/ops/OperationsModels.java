package cn.bike.platform.ops;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class OperationsModels {

    private OperationsModels() {
    }

    public enum TaskType {
        BATTERY_SWAP, REBALANCE, REPAIR, INSPECTION, RETRIEVAL, CLEANING
    }

    public enum TaskStatus {
        OPEN, CLAIMED, IN_PROGRESS, COMPLETED, CANCELLED
    }

    public enum TaskPriority {
        LOW, NORMAL, HIGH, URGENT
    }

    public enum TaskEventType {
        CREATED, CLAIMED, ASSIGNED, RELEASED, STARTED, COMPLETED, CANCELLED
    }

    public record CreateTaskRequest(
            @NotNull TaskType taskType,
            @NotNull TaskPriority priority,
            @NotBlank @Size(max = 100) String title,
            @Size(max = 500) String description,
            @NotBlank @Size(max = 32) String vehicleId,
            @NotBlank @Size(max = 36) String orgId,
            @Size(max = 100) String targetName,
            Instant dueAt,
            @Size(max = 36) String assigneeId
    ) {
    }

    public record AssignmentRequest(@NotBlank @Size(max = 36) String assigneeId) {
    }

    public record CompletionRequest(@NotBlank @Size(max = 500) String resultNote) {
    }

    public record CancellationRequest(@NotBlank @Size(max = 500) String reason) {
    }

    public record TaskItem(
            String taskId,
            String taskNo,
            TaskType taskType,
            TaskStatus status,
            TaskPriority priority,
            String title,
            String description,
            String vehicleId,
            String plateNumber,
            String cityCode,
            String areaCode,
            String orgId,
            String orgName,
            String targetName,
            BigDecimal sourceLongitude,
            BigDecimal sourceLatitude,
            Integer batteryPercent,
            String assigneeId,
            String assigneeName,
            String createdBy,
            String createdByName,
            Instant dueAt,
            Instant claimedAt,
            Instant startedAt,
            Instant completedAt,
            String resultNote,
            int version,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record TaskEvent(
            long eventId,
            TaskEventType eventType,
            TaskStatus fromStatus,
            TaskStatus toStatus,
            String actorId,
            String actorName,
            String note,
            Instant createdAt
    ) {
    }

    public record TaskDetail(TaskItem task, List<TaskEvent> events) {
    }

    public record TaskPage(List<TaskItem> items, long total, int page, int pageSize) {
    }

    public record TaskSummary(
            long openCount,
            long claimedCount,
            long inProgressCount,
            long overdueCount,
            long completedTodayCount,
            long myActiveCount
    ) {
    }

    public record AssigneeOption(
            String userId,
            String displayName,
            String phone,
            String orgId,
            String orgName
    ) {
    }

    record VehicleSnapshot(
            String vehicleId,
            String cityCode,
            String areaCode,
            BigDecimal longitude,
            BigDecimal latitude,
            Integer batteryPercent
    ) {
    }

    record OrganizationSnapshot(String orgId, String cityCode, boolean active) {
    }
}

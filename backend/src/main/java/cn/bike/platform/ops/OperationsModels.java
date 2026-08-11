package cn.bike.platform.ops;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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
        OPEN, CLAIMED, IN_PROGRESS, PENDING_REVIEW, EXCEPTION, COMPLETED, CANCELLED
    }

    public enum TaskPriority {
        LOW, NORMAL, HIGH, URGENT
    }

    public enum TaskSourceType {
        MANUAL, RULE, BATCH
    }

    public enum TaskEventType {
        CREATED, CLAIMED, ASSIGNED, RELEASED, STARTED, SUBMITTED, COMPLETED, CANCELLED,
        DEDUPLICATED, RULE_RECOVERED, EXCEPTION_REPORTED, EXCEPTION_RESOLVED,
        REVIEW_APPROVED, REVIEW_REJECTED
    }

    public enum TriggerType {
        LOW_BATTERY, VEHICLE_FAULT, VEHICLE_OFFLINE, GEO_VIOLATION
    }

    public enum ExceptionType {
        VEHICLE_NOT_FOUND, ACCESS_BLOCKED, SAFETY_RISK, PARTS_SHORTAGE, OTHER
    }

    public enum ExceptionResolutionAction {
        REOPEN, CLOSE
    }

    public enum ReviewAction {
        APPROVE, REJECT
    }

    public enum ReviewStatus {
        PENDING, APPROVED, REJECTED
    }

    public enum AttachmentPurpose {
        BEFORE, AFTER, EXCEPTION
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

    public record BatchCreateTaskRequest(
            @NotBlank @Size(max = 100) String batchName,
            @NotNull TaskType taskType,
            @NotNull TaskPriority priority,
            @NotBlank @Size(max = 100) String title,
            @Size(max = 500) String description,
            @NotEmpty @Size(max = 200) List<@NotBlank @Size(max = 32) String> vehicleIds,
            @NotBlank @Size(max = 36) String orgId,
            @Size(max = 100) String targetName,
            Instant dueAt,
            @Size(max = 36) String assigneeId
    ) {
    }

    public record AssignmentRequest(@NotBlank @Size(max = 36) String assigneeId) {
    }

    public record CompletionRequest(
            @NotBlank @Size(max = 500) String resultNote,
            @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal arrivalLongitude,
            @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal arrivalLatitude,
            @NotEmpty @Size(max = 30) List<@NotBlank @Size(max = 100) String> checklist,
            @Size(max = 64) String removedBatteryId,
            @Size(max = 64) String installedBatteryId,
            @Size(max = 50) List<@NotBlank @Size(max = 100) String> partsUsed,
            @DecimalMin("-180") @DecimalMax("180") BigDecimal targetLongitude,
            @DecimalMin("-90") @DecimalMax("90") BigDecimal targetLatitude,
            @Size(max = 20) List<Long> beforeAttachmentIds,
            @NotEmpty @Size(max = 20) List<Long> afterAttachmentIds
    ) {
    }

    public record CancellationRequest(@NotBlank @Size(max = 500) String reason) {
    }

    public record ExceptionRequest(
            @NotNull ExceptionType exceptionType,
            @NotBlank @Size(max = 500) String note,
            @Size(max = 20) List<Long> attachmentIds
    ) {
    }

    public record ExceptionResolutionRequest(
            @NotNull ExceptionResolutionAction action,
            @NotBlank @Size(max = 500) String note
    ) {
    }

    public record ReviewRequest(
            @NotNull ReviewAction action,
            @Size(max = 500) String note
    ) {
    }

    public record TaskRuleRequest(
            @NotBlank @Size(max = 100) String ruleName,
            @NotBlank @Size(min = 6, max = 6) String cityCode,
            @NotBlank @Size(max = 36) String orgId,
            @NotNull TriggerType triggerType,
            @Min(1) @Max(99) Integer thresholdValue,
            @NotNull TaskType taskType,
            @NotNull TaskPriority priority,
            @NotBlank @Size(max = 100) String titleTemplate,
            @Size(max = 500) String descriptionTemplate,
            @NotNull @Min(5) @Max(10080) Integer dueMinutes,
            @NotNull @Min(0) @Max(10080) Integer cooldownMinutes,
            boolean autoClose,
            boolean enabled
    ) {
    }

    public record RouteOptimizationRequest(
            @NotEmpty @Size(max = 16) List<@NotBlank @Size(max = 36) String> taskIds,
            @DecimalMin("-180") @DecimalMax("180") BigDecimal startLongitude,
            @DecimalMin("-90") @DecimalMax("90") BigDecimal startLatitude
    ) {
    }

    public record TaskItem(
            String taskId,
            String taskNo,
            TaskType taskType,
            TaskStatus status,
            TaskPriority priority,
            TaskSourceType sourceType,
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
            String ruleId,
            String ruleName,
            String batchId,
            String batchNo,
            String triggerKey,
            int duplicateCount,
            Instant dueAt,
            Instant claimedAt,
            Instant startedAt,
            Instant submittedAt,
            Instant completedAt,
            String resultNote,
            ExceptionType exceptionType,
            String exceptionNote,
            Instant exceptionAt,
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

    public record EvidenceAttachment(
            long attachmentId,
            AttachmentPurpose purpose,
            String originalName,
            String contentType,
            long sizeBytes,
            String downloadUrl,
            Instant uploadedAt
    ) {
    }

    public record TaskEvidence(
            long evidenceId,
            int submissionNo,
            String resultNote,
            BigDecimal arrivalLongitude,
            BigDecimal arrivalLatitude,
            List<String> checklist,
            String removedBatteryId,
            String installedBatteryId,
            List<String> partsUsed,
            BigDecimal targetLongitude,
            BigDecimal targetLatitude,
            ReviewStatus reviewStatus,
            String submittedBy,
            String submittedByName,
            Instant submittedAt,
            String reviewedByName,
            String reviewNote,
            Instant reviewedAt,
            List<EvidenceAttachment> attachments
    ) {
    }

    public record TaskException(
            long exceptionId,
            ExceptionType exceptionType,
            String note,
            String reportedBy,
            String reportedByName,
            Instant reportedAt,
            ExceptionResolutionAction resolutionAction,
            String resolutionNote,
            String resolvedByName,
            Instant resolvedAt,
            List<EvidenceAttachment> attachments
    ) {
    }

    public record TaskTrigger(
            long triggerId,
            String ruleId,
            String ruleName,
            String triggerKey,
            boolean active,
            int occurrenceCount,
            Instant firstTriggeredAt,
            Instant lastTriggeredAt,
            Instant recoveredAt
    ) {
    }

    public record TaskDetail(
            TaskItem task,
            List<TaskEvent> events,
            List<TaskEvidence> evidence,
            List<TaskException> exceptions,
            List<TaskTrigger> triggers
    ) {
    }

    public record TaskPage(List<TaskItem> items, long total, int page, int pageSize) {
    }

    public record TaskSummary(
            long openCount,
            long claimedCount,
            long inProgressCount,
            long pendingReviewCount,
            long exceptionCount,
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

    public record TaskRule(
            String ruleId,
            String ruleName,
            String cityCode,
            String orgId,
            String orgName,
            TriggerType triggerType,
            Integer thresholdValue,
            TaskType taskType,
            TaskPriority priority,
            String titleTemplate,
            String descriptionTemplate,
            int dueMinutes,
            int cooldownMinutes,
            boolean autoClose,
            boolean enabled,
            int version,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record BatchSkippedItem(String vehicleId, String reason) {
    }

    public record BatchCreateResult(
            String batchId,
            String batchNo,
            int requestedCount,
            List<TaskItem> createdTasks,
            List<BatchSkippedItem> skipped
    ) {
    }

    public record RouteCoordinate(BigDecimal longitude, BigDecimal latitude) {
    }

    public record RouteStop(
            int sequence,
            String taskId,
            String taskNo,
            String vehicleId,
            String title,
            BigDecimal longitude,
            BigDecimal latitude,
            long legDistanceMeters,
            long legDurationSeconds
    ) {
    }

    public record RoutePlan(
            String provider,
            String coordinateSystem,
            String warning,
            long totalDistanceMeters,
            long totalDurationSeconds,
            List<RouteStop> stops,
            List<RouteCoordinate> polyline
    ) {
    }

    public record AttachmentUploadResult(
            long attachmentId,
            AttachmentPurpose purpose,
            String originalName,
            String contentType,
            long sizeBytes,
            String downloadUrl,
            Instant uploadedAt
    ) {
    }

    public record AutomationScanResult(int scannedVehicles, int createdTasks, int deduplicatedSignals) {
    }

    public record VehicleSnapshot(
            String vehicleId,
            String cityCode,
            String areaCode,
            BigDecimal longitude,
            BigDecimal latitude,
            Integer batteryPercent
    ) {
    }

    public record OrganizationSnapshot(String orgId, String cityCode, boolean active) {
    }

    record AutomationVehicleState(
            String vehicleId,
            String cityCode,
            String areaCode,
            BigDecimal longitude,
            BigDecimal latitude,
            Integer batteryPercent,
            boolean online,
            String controllerStatus,
            String rideStatus,
            List<String> faultCodes,
            Instant occurredAt
    ) {
    }

    public record StoredAttachment(
            long attachmentId,
            String taskId,
            AttachmentPurpose purpose,
            String originalName,
            String storedName,
            String contentType,
            long sizeBytes,
            String sha256,
            String storagePath,
            String uploadedBy,
            Instant uploadedAt
    ) {
    }
}

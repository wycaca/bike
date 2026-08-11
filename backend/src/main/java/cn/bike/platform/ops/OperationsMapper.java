package cn.bike.platform.ops;

import cn.bike.platform.ops.OperationsModels.AssigneeOption;
import cn.bike.platform.ops.OperationsModels.AttachmentPurpose;
import cn.bike.platform.ops.OperationsModels.AutomationVehicleState;
import cn.bike.platform.ops.OperationsModels.ExceptionResolutionAction;
import cn.bike.platform.ops.OperationsModels.ExceptionType;
import cn.bike.platform.ops.OperationsModels.OrganizationSnapshot;
import cn.bike.platform.ops.OperationsModels.ReviewStatus;
import cn.bike.platform.ops.OperationsModels.StoredAttachment;
import cn.bike.platform.ops.OperationsModels.TaskEvent;
import cn.bike.platform.ops.OperationsModels.TaskException;
import cn.bike.platform.ops.OperationsModels.TaskItem;
import cn.bike.platform.ops.OperationsModels.TaskStatus;
import cn.bike.platform.ops.OperationsModels.TaskSummary;
import cn.bike.platform.ops.OperationsModels.TaskTrigger;
import cn.bike.platform.ops.OperationsModels.VehicleSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Mapper
public interface OperationsMapper {

    List<TaskItem> findTasks(
            @Param("cityCode") String cityCode,
            @Param("status") String status,
            @Param("type") String type,
            @Param("scope") String scope,
            @Param("currentUserId") String currentUserId,
            @Param("keyword") String keyword,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    long countTasks(
            @Param("cityCode") String cityCode,
            @Param("status") String status,
            @Param("type") String type,
            @Param("scope") String scope,
            @Param("currentUserId") String currentUserId,
            @Param("keyword") String keyword
    );

    TaskSummary summary(@Param("cityCode") String cityCode, @Param("currentUserId") String currentUserId);

    TaskItem findTask(@Param("taskId") String taskId);

    List<TaskItem> findTasksByIds(@Param("taskIds") List<String> taskIds);

    TaskItem findActiveTaskForVehicle(@Param("vehicleId") String vehicleId);

    List<TaskEvent> findEvents(@Param("taskId") String taskId);

    VehicleSnapshot findVehicleSnapshot(@Param("vehicleId") String vehicleId);

    OrganizationSnapshot findOrganization(@Param("orgId") String orgId);

    AssigneeOption findEligibleAssignee(@Param("userId") String userId, @Param("cityCode") String cityCode);

    List<AssigneeOption> findAssignees(@Param("cityCode") String cityCode);

    int insertTask(TaskInsert row);

    int insertBatch(
            @Param("batchId") String batchId,
            @Param("batchNo") String batchNo,
            @Param("batchName") String batchName,
            @Param("cityCode") String cityCode,
            @Param("orgId") String orgId,
            @Param("taskType") String taskType,
            @Param("requestedCount") int requestedCount,
            @Param("createdBy") String createdBy
    );

    int updateBatchCounts(
            @Param("batchId") String batchId,
            @Param("createdCount") int createdCount,
            @Param("skippedCount") int skippedCount
    );

    int claim(@Param("taskId") String taskId, @Param("assigneeId") String assigneeId);

    int assign(@Param("taskId") String taskId, @Param("version") int version,
               @Param("assigneeId") String assigneeId);

    int release(@Param("taskId") String taskId, @Param("version") int version,
                @Param("assigneeId") String assigneeId);

    int start(@Param("taskId") String taskId, @Param("version") int version,
              @Param("assigneeId") String assigneeId);

    int submitForReview(@Param("taskId") String taskId, @Param("version") int version,
                        @Param("assigneeId") String assigneeId, @Param("resultNote") String resultNote);

    int approve(@Param("taskId") String taskId, @Param("version") int version);

    int reject(@Param("taskId") String taskId, @Param("version") int version);

    int cancel(@Param("taskId") String taskId, @Param("version") int version,
               @Param("reason") String reason);

    int reportException(@Param("taskId") String taskId, @Param("version") int version,
                        @Param("assigneeId") String assigneeId, @Param("type") String type,
                        @Param("note") String note);

    int resolveException(@Param("taskId") String taskId, @Param("version") int version,
                         @Param("targetStatus") String targetStatus, @Param("note") String note);

    int updateVehicleLifecycle(@Param("vehicleId") String vehicleId, @Param("status") String status);

    long insertEvidence(EvidenceInsert row);

    int reviewLatestEvidence(@Param("taskId") String taskId, @Param("status") String status,
                             @Param("reviewedBy") String reviewedBy,
                             @Param("reviewedByName") String reviewedByName,
                             @Param("note") String note);

    long insertAttachment(AttachmentInsert row);

    StoredAttachment findAttachment(@Param("attachmentId") long attachmentId);

    List<StoredAttachment> findAttachments(@Param("attachmentIds") List<Long> attachmentIds);

    int linkEvidenceAttachments(@Param("evidenceId") long evidenceId,
                                @Param("attachmentIds") List<Long> attachmentIds,
                                @Param("purpose") String purpose);

    long insertException(ExceptionInsert row);

    int linkExceptionAttachments(@Param("exceptionId") long exceptionId,
                                 @Param("attachmentIds") List<Long> attachmentIds);

    int resolveLatestException(@Param("taskId") String taskId, @Param("action") String action,
                               @Param("note") String note, @Param("resolvedBy") String resolvedBy,
                               @Param("resolvedByName") String resolvedByName);

    int upsertTrigger(@Param("taskId") String taskId, @Param("ruleId") String ruleId,
                      @Param("triggerKey") String triggerKey, @Param("occurredAt") Instant occurredAt,
                      @Param("payload") String payload);

    int incrementDuplicateCount(@Param("taskId") String taskId);

    int recoverTriggers(@Param("ruleId") String ruleId, @Param("vehicleId") String vehicleId,
                        @Param("recoveredAt") Instant recoveredAt);

    TaskItem findRuleTaskWithoutActiveTriggers(@Param("vehicleId") String vehicleId);

    boolean hasRecentRuleTask(@Param("vehicleId") String vehicleId, @Param("ruleId") String ruleId,
                              @Param("cutoff") Instant cutoff);

    List<AutomationRow> findAutomationVehicleStates(@Param("cityCode") String cityCode);

    boolean hasGeoViolation(@Param("cityCode") String cityCode, @Param("rideStatus") String rideStatus,
                            @Param("longitude") BigDecimal longitude, @Param("latitude") BigDecimal latitude);

    int insertEvent(@Param("taskId") String taskId, @Param("eventType") String eventType,
                    @Param("fromStatus") String fromStatus, @Param("toStatus") String toStatus,
                    @Param("actorId") String actorId, @Param("actorName") String actorName,
                    @Param("note") String note);

    List<EvidenceRow> findEvidence(@Param("taskId") String taskId);

    List<ExceptionRow> findExceptions(@Param("taskId") String taskId);

    List<TaskTrigger> findTriggers(@Param("taskId") String taskId);

    List<EvidenceAttachmentRow> findEvidenceAttachments(@Param("evidenceId") long evidenceId);

    List<EvidenceAttachmentRow> findExceptionAttachments(@Param("exceptionId") long exceptionId);

    int insertMockTask(MockTask row);

    int insertMockEvent(@Param("taskId") String taskId, @Param("eventType") String eventType,
                        @Param("fromStatus") String fromStatus, @Param("toStatus") String toStatus,
                        @Param("actorId") String actorId, @Param("actorName") String actorName,
                        @Param("note") String note);

    int updateMockVehicleLifecycle(@Param("vehicleId") String vehicleId, @Param("status") String status);

    record TaskInsert(
            String taskId,
            String taskNo,
            String taskType,
            String taskStatus,
            String priority,
            String sourceType,
            String title,
            String description,
            String vehicleId,
            String cityCode,
            String areaCode,
            String orgId,
            String targetName,
            BigDecimal longitude,
            BigDecimal latitude,
            Integer batteryPercent,
            String assigneeId,
            String createdBy,
            String ruleId,
            String batchId,
            String triggerKey,
            Instant dueAt
    ) {
    }

    record EvidenceInsert(
            String taskId,
            String resultNote,
            BigDecimal arrivalLongitude,
            BigDecimal arrivalLatitude,
            String checklist,
            String removedBatteryId,
            String installedBatteryId,
            String partsUsed,
            BigDecimal targetLongitude,
            BigDecimal targetLatitude,
            String submittedBy,
            String submittedByName
    ) {
    }

    record AttachmentInsert(
            String taskId,
            String purpose,
            String originalName,
            String storedName,
            String contentType,
            long sizeBytes,
            String sha256,
            String storagePath,
            String uploadedBy
    ) {
    }

    record ExceptionInsert(
            String taskId,
            String type,
            String note,
            String reportedBy,
            String reportedByName
    ) {
    }

    record EvidenceRow(
            long evidenceId,
            int submissionNo,
            String resultNote,
            BigDecimal arrivalLongitude,
            BigDecimal arrivalLatitude,
            String checklist,
            String removedBatteryId,
            String installedBatteryId,
            String partsUsed,
            BigDecimal targetLongitude,
            BigDecimal targetLatitude,
            ReviewStatus reviewStatus,
            String submittedBy,
            String submittedByName,
            Instant submittedAt,
            String reviewedByName,
            String reviewNote,
            Instant reviewedAt
    ) {
    }

    record ExceptionRow(
            long exceptionId,
            ExceptionType exceptionType,
            String note,
            String reportedBy,
            String reportedByName,
            Instant reportedAt,
            ExceptionResolutionAction resolutionAction,
            String resolutionNote,
            String resolvedByName,
            Instant resolvedAt
    ) {
    }

    record EvidenceAttachmentRow(
            long attachmentId,
            AttachmentPurpose purpose,
            String originalName,
            String contentType,
            long sizeBytes,
            Instant uploadedAt
    ) {
    }

    record AutomationRow(
            String vehicleId,
            String cityCode,
            String areaCode,
            BigDecimal longitude,
            BigDecimal latitude,
            Integer batteryPercent,
            boolean online,
            String controllerStatus,
            String rideStatus,
            String faultCodes,
            Instant occurredAt
    ) {
    }

    record MockTask(
            String taskId,
            String taskNo,
            String taskType,
            String taskStatus,
            String priority,
            String title,
            String vehicleId,
            String orgId,
            String targetName,
            String assigneeId,
            Instant dueAt,
            String resultNote
    ) {
    }
}

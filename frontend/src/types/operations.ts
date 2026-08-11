export type UserRole = 'ADMIN' | 'OPERATOR' | 'AUDITOR'
export type RecordStatus = 'ACTIVE' | 'DISABLED'
export type OrganizationType = 'COMPANY' | 'REGION' | 'TEAM'
export type FenceType = 'OPERATION' | 'NO_RIDE' | 'NO_PARK'
export type RevenueGranularity = 'DAY' | 'MONTH'
export type ReportExportStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'EXPIRED'
export type OperationsTaskType = 'BATTERY_SWAP' | 'REBALANCE' | 'REPAIR' | 'INSPECTION' | 'RETRIEVAL' | 'CLEANING'
export type OperationsTaskStatus = 'OPEN' | 'CLAIMED' | 'IN_PROGRESS' | 'PENDING_REVIEW' | 'EXCEPTION' | 'COMPLETED' | 'CANCELLED'
export type OperationsTaskPriority = 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT'
export type OperationsTaskScope = 'ALL' | 'MINE' | 'UNASSIGNED'
export type OperationsTaskSource = 'MANUAL' | 'RULE' | 'BATCH'
export type OperationsTaskEventType = 'CREATED' | 'CLAIMED' | 'ASSIGNED' | 'RELEASED' | 'STARTED' | 'SUBMITTED' | 'COMPLETED' | 'CANCELLED' | 'DEDUPLICATED' | 'RULE_RECOVERED' | 'EXCEPTION_REPORTED' | 'EXCEPTION_RESOLVED' | 'REVIEW_APPROVED' | 'REVIEW_REJECTED'
export type OperationsTriggerType = 'LOW_BATTERY' | 'VEHICLE_FAULT' | 'VEHICLE_OFFLINE' | 'GEO_VIOLATION'
export type OperationsExceptionType = 'VEHICLE_NOT_FOUND' | 'ACCESS_BLOCKED' | 'SAFETY_RISK' | 'PARTS_SHORTAGE' | 'OTHER'
export type OperationsReviewStatus = 'PENDING' | 'APPROVED' | 'REJECTED'
export type OperationsAttachmentPurpose = 'BEFORE' | 'AFTER' | 'EXCEPTION'

export interface CurrentUser {
  userId: string
  username: string
  displayName: string
  orgId: string
  orgName: string
  role: UserRole
}

export interface Organization {
  orgId: string
  parentOrgId: string | null
  orgName: string
  orgType: OrganizationType
  cityCode: string | null
  status: RecordStatus
  createdAt: string
  updatedAt: string
}

export interface OrganizationRequest {
  parentOrgId: string | null
  orgName: string
  orgType: OrganizationType
  cityCode: string
  status: RecordStatus
}

export interface PlatformUser {
  userId: string
  username: string
  displayName: string
  phone: string | null
  orgId: string
  orgName: string
  role: UserRole
  status: RecordStatus
  lastLoginAt: string | null
  createdAt: string
}

export interface UserRequest {
  username: string
  displayName: string
  phone: string
  orgId: string
  role: UserRole
  status: RecordStatus
  password: string | null
}

export interface AuditLog {
  auditId: number
  userId: string | null
  username: string | null
  orgId: string | null
  action: string
  resourceType: string
  resourceId: string | null
  requestMethod: string
  requestPath: string
  clientIp: string | null
  statusCode: number
  durationMs: number
  detail: string | null
  createdAt: string
}

export interface PagedData<T> {
  items: T[]
  total: number
  page: number
  pageSize: number
}

export interface Coordinate {
  longitude: number
  latitude: number
}

export interface GeofenceRequest {
  fenceName: string
  cityCode: string
  fenceType: FenceType
  orgId: string
  status: RecordStatus
  boundary: Coordinate[]
}

export interface Geofence extends GeofenceRequest {
  fenceId: string
  orgName: string
  areaSquareMeters: number
  updatedAt: string
}

export interface ParkingPointRequest {
  pointName: string
  cityCode: string
  orgId: string
  status: RecordStatus
  location: Coordinate
  radiusMeters: number
  capacity: number
}

export interface ParkingPoint extends ParkingPointRequest {
  pointId: string
  orgName: string
  vehicleCount: number
  updatedAt: string
}

export interface GeoViolation {
  vehicleId: string
  violationType: string
  facilityId: string | null
  facilityName: string | null
  longitude: number
  latitude: number
  batteryPercent: number | null
  reportedAt: string
}

export interface GeoOverview {
  fences: Geofence[]
  parkingPoints: ParkingPoint[]
  violations: GeoViolation[]
}

export interface DashboardSummary {
  totalVehicles: number
  onlineVehicles: number
  ridingVehicles: number
  offlineVehicles: number
  lowBatteryVehicles: number
  faultVehicles: number
  maintenanceVehicles: number
  onlineRate: number
}

export interface DailyTrend {
  date: string
  activeVehicles: number
  telemetryReports: number
  averageBattery: number
}

export interface AreaDistribution {
  areaCode: string
  vehicleCount: number
  onlineCount: number
  lowBatteryCount: number
  faultCount: number
}

export interface DashboardData {
  summary: DashboardSummary
  trends: DailyTrend[]
  areas: AreaDistribution[]
  generatedAt: string
}

export interface RevenueValues {
  grossBookings: number
  discountAmount: number
  refundAmount: number
  netRevenue: number
  completedRides: number
  activeVehicles: number
  vehicleDays: number
  averageDeployedVehicles: number
  ridesPerVehicleDay: number
  averageRevenuePerRide: number
  revenuePerVehicleDay: number
  discountRate: number
  refundRate: number
  averageRideDurationMinutes: number
  averageRideDistanceKm: number
}

export interface RevenuePeriod {
  periodStart: string
  periodEnd: string
  values: RevenueValues
}

export interface RevenueReport {
  cityCode: string
  granularity: RevenueGranularity
  summary: {
    fromDate: string
    toDate: string
    values: RevenueValues
  }
  periods: RevenuePeriod[]
  generatedAt: string
}

export interface ReportExportJob {
  jobId: string
  reportType: 'REVENUE' | 'VEHICLE_STATUS'
  status: ReportExportStatus
  outputFileName: string
  fileSizeBytes: number | null
  rowCount: number | null
  errorMessage: string | null
  createdAt: string
  startedAt: string | null
  completedAt: string | null
  expiresAt: string | null
  downloadable: boolean
}

export interface OperationsTask {
  taskId: string
  taskNo: string
  taskType: OperationsTaskType
  status: OperationsTaskStatus
  priority: OperationsTaskPriority
  sourceType: OperationsTaskSource
  title: string
  description: string | null
  vehicleId: string
  plateNumber: string | null
  cityCode: string
  areaCode: string
  orgId: string
  orgName: string
  targetName: string | null
  sourceLongitude: number | null
  sourceLatitude: number | null
  batteryPercent: number | null
  assigneeId: string | null
  assigneeName: string | null
  createdBy: string | null
  createdByName: string | null
  ruleId: string | null
  ruleName: string | null
  batchId: string | null
  batchNo: string | null
  triggerKey: string | null
  duplicateCount: number
  dueAt: string | null
  claimedAt: string | null
  startedAt: string | null
  submittedAt: string | null
  completedAt: string | null
  resultNote: string | null
  exceptionType: OperationsExceptionType | null
  exceptionNote: string | null
  exceptionAt: string | null
  version: number
  createdAt: string
  updatedAt: string
}

export interface OperationsTaskEvent {
  eventId: number
  eventType: OperationsTaskEventType
  fromStatus: OperationsTaskStatus | null
  toStatus: OperationsTaskStatus
  actorId: string | null
  actorName: string
  note: string | null
  createdAt: string
}

export interface OperationsTaskDetail {
  task: OperationsTask
  events: OperationsTaskEvent[]
  evidence: OperationsTaskEvidence[]
  exceptions: OperationsTaskException[]
  triggers: OperationsTaskTrigger[]
}

export interface OperationsTaskSummary {
  openCount: number
  claimedCount: number
  inProgressCount: number
  pendingReviewCount: number
  exceptionCount: number
  overdueCount: number
  completedTodayCount: number
  myActiveCount: number
}

export interface OperationsAssignee {
  userId: string
  displayName: string
  phone: string | null
  orgId: string
  orgName: string
}

export interface OperationsTaskRequest {
  taskType: OperationsTaskType
  priority: OperationsTaskPriority
  title: string
  description: string | null
  vehicleId: string
  orgId: string
  targetName: string | null
  dueAt: string | null
  assigneeId: string | null
}

export interface OperationsBatchTaskRequest extends Omit<OperationsTaskRequest, 'vehicleId'> {
  batchName: string
  vehicleIds: string[]
}

export interface OperationsBatchResult {
  batchId: string
  batchNo: string
  requestedCount: number
  createdTasks: OperationsTask[]
  skipped: Array<{ vehicleId: string; reason: string }>
}

export interface OperationsCompletionRequest {
  resultNote: string
  arrivalLongitude: number
  arrivalLatitude: number
  checklist: string[]
  removedBatteryId: string | null
  installedBatteryId: string | null
  partsUsed: string[]
  targetLongitude: number | null
  targetLatitude: number | null
  beforeAttachmentIds: number[]
  afterAttachmentIds: number[]
}

export interface OperationsAttachment {
  attachmentId: number
  purpose: OperationsAttachmentPurpose
  originalName: string
  contentType: string
  sizeBytes: number
  downloadUrl: string
  uploadedAt: string
}

export interface OperationsTaskEvidence {
  evidenceId: number
  submissionNo: number
  resultNote: string
  arrivalLongitude: number
  arrivalLatitude: number
  checklist: string[]
  removedBatteryId: string | null
  installedBatteryId: string | null
  partsUsed: string[]
  targetLongitude: number | null
  targetLatitude: number | null
  reviewStatus: OperationsReviewStatus
  submittedBy: string
  submittedByName: string
  submittedAt: string
  reviewedByName: string | null
  reviewNote: string | null
  reviewedAt: string | null
  attachments: OperationsAttachment[]
}

export interface OperationsTaskException {
  exceptionId: number
  exceptionType: OperationsExceptionType
  note: string
  reportedBy: string
  reportedByName: string
  reportedAt: string
  resolutionAction: 'REOPEN' | 'CLOSE' | null
  resolutionNote: string | null
  resolvedByName: string | null
  resolvedAt: string | null
  attachments: OperationsAttachment[]
}

export interface OperationsTaskTrigger {
  triggerId: number
  ruleId: string
  ruleName: string
  triggerKey: string
  active: boolean
  occurrenceCount: number
  firstTriggeredAt: string
  lastTriggeredAt: string
  recoveredAt: string | null
}

export interface OperationsRuleRequest {
  ruleName: string
  cityCode: string
  orgId: string
  triggerType: OperationsTriggerType
  thresholdValue: number | null
  taskType: OperationsTaskType
  priority: OperationsTaskPriority
  titleTemplate: string
  descriptionTemplate: string | null
  dueMinutes: number
  cooldownMinutes: number
  autoClose: boolean
  enabled: boolean
}

export interface OperationsRule extends OperationsRuleRequest {
  ruleId: string
  orgName: string
  version: number
  createdAt: string
  updatedAt: string
}

export interface OperationsRoutePlan {
  provider: 'AMAP' | 'LOCAL_ESTIMATE'
  coordinateSystem: 'GCJ02' | 'WGS84'
  warning: string | null
  totalDistanceMeters: number
  totalDurationSeconds: number
  stops: Array<{
    sequence: number
    taskId: string
    taskNo: string
    vehicleId: string
    title: string
    longitude: number
    latitude: number
    legDistanceMeters: number
    legDurationSeconds: number
  }>
  polyline: Coordinate[]
}

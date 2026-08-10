export type UserRole = 'ADMIN' | 'OPERATOR'
export type TaskType = 'BATTERY_SWAP' | 'REBALANCE' | 'REPAIR' | 'INSPECTION' | 'RETRIEVAL' | 'CLEANING'
export type TaskStatus = 'OPEN' | 'CLAIMED' | 'IN_PROGRESS' | 'PENDING_REVIEW' | 'EXCEPTION' | 'COMPLETED' | 'CANCELLED'
export type TaskPriority = 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT'
export type ExceptionType = 'VEHICLE_NOT_FOUND' | 'ACCESS_BLOCKED' | 'SAFETY_RISK' | 'PARTS_SHORTAGE' | 'OTHER'

export interface ApiResponse<T> { code: number; message: string; data: T }

export interface CurrentUser {
  userId: string
  username: string
  displayName: string
  orgId: string
  orgName: string
  role: UserRole
}

export interface Task {
  taskId: string
  taskNo: string
  taskType: TaskType
  status: TaskStatus
  priority: TaskPriority
  sourceType: 'MANUAL' | 'RULE' | 'BATCH'
  title: string
  description: string | null
  vehicleId: string
  orgName: string
  sourceLongitude: number | null
  sourceLatitude: number | null
  batteryPercent: number | null
  assigneeId: string | null
  assigneeName: string | null
  dueAt: string | null
  duplicateCount: number
  exceptionType: ExceptionType | null
  exceptionNote: string | null
}

export interface TaskPage { items: Task[]; total: number; page: number; pageSize: number }
export interface TaskSummary {
  openCount: number
  claimedCount: number
  inProgressCount: number
  pendingReviewCount: number
  exceptionCount: number
  overdueCount: number
  completedTodayCount: number
  myActiveCount: number
}

export interface TaskDetail {
  task: Task
  events: Array<{ eventId: number; eventType: string; actorName: string; note: string | null; createdAt: string }>
  evidence: Array<{ evidenceId: number; submissionNo: number; resultNote: string; reviewStatus: string; submittedByName: string; submittedAt: string; attachments: Attachment[] }>
  exceptions: Array<{ exceptionId: number; exceptionType: ExceptionType; note: string; reportedByName: string; reportedAt: string; resolutionNote: string | null; resolvedAt: string | null; attachments: Attachment[] }>
  triggers: Array<{ triggerId: number; ruleName: string; active: boolean; occurrenceCount: number; lastTriggeredAt: string }>
}

export interface Attachment {
  attachmentId: number
  purpose: 'BEFORE' | 'AFTER' | 'EXCEPTION'
  originalName: string
  downloadUrl: string
}

export interface TaskRule {
  ruleId: string
  ruleName: string
  triggerType: 'LOW_BATTERY' | 'VEHICLE_FAULT' | 'VEHICLE_OFFLINE' | 'GEO_VIOLATION'
  thresholdValue: number | null
  taskType: TaskType
  enabled: boolean
  version: number
  cityCode: string
  orgId: string
  priority: TaskPriority
  titleTemplate: string
  descriptionTemplate: string | null
  dueMinutes: number
  cooldownMinutes: number
  autoClose: boolean
}

export interface Assignee {
  userId: string
  displayName: string
  phone: string | null
  orgId: string
  orgName: string
}

export interface BatchCreateResult {
  batchId: string
  batchNo: string
  requestedCount: number
  createdTasks: Task[]
  skipped: Array<{ vehicleId: string; reason: string }>
}

export interface RoutePlan {
  provider: 'AMAP' | 'LOCAL_ESTIMATE'
  warning: string | null
  totalDistanceMeters: number
  totalDurationSeconds: number
  stops: Array<{ sequence: number; taskId: string; vehicleId: string; title: string; legDistanceMeters: number; legDurationSeconds: number }>
}

export interface Vehicle {
  vehicleId: string
  plateNumber: string | null
  lifecycleStatus: string
  latestState: { batteryPercent: number | null; online: boolean; controllerStatus: string } | null
}

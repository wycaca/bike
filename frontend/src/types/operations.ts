export type UserRole = 'ADMIN' | 'OPERATOR' | 'AUDITOR'
export type RecordStatus = 'ACTIVE' | 'DISABLED'
export type OrganizationType = 'COMPANY' | 'REGION' | 'TEAM'
export type FenceType = 'OPERATION' | 'NO_RIDE' | 'NO_PARK'
export type RevenueGranularity = 'DAY' | 'MONTH'
export type ReportExportStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'EXPIRED'
export type OperationsTaskType = 'BATTERY_SWAP' | 'REBALANCE' | 'REPAIR' | 'INSPECTION' | 'RETRIEVAL' | 'CLEANING'
export type OperationsTaskStatus = 'OPEN' | 'CLAIMED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'
export type OperationsTaskPriority = 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT'
export type OperationsTaskScope = 'ALL' | 'MINE' | 'UNASSIGNED'
export type OperationsTaskEventType = 'CREATED' | 'CLAIMED' | 'ASSIGNED' | 'RELEASED' | 'STARTED' | 'COMPLETED' | 'CANCELLED'

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
  reportType: 'REVENUE'
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
  createdBy: string
  createdByName: string
  dueAt: string | null
  claimedAt: string | null
  startedAt: string | null
  completedAt: string | null
  resultNote: string | null
  version: number
  createdAt: string
  updatedAt: string
}

export interface OperationsTaskEvent {
  eventId: number
  eventType: OperationsTaskEventType
  fromStatus: OperationsTaskStatus | null
  toStatus: OperationsTaskStatus
  actorId: string
  actorName: string
  note: string | null
  createdAt: string
}

export interface OperationsTaskDetail {
  task: OperationsTask
  events: OperationsTaskEvent[]
}

export interface OperationsTaskSummary {
  openCount: number
  claimedCount: number
  inProgressCount: number
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

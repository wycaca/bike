export type UserRole = 'ADMIN' | 'OPERATOR' | 'AUDITOR'
export type RecordStatus = 'ACTIVE' | 'DISABLED'
export type OrganizationType = 'COMPANY' | 'REGION' | 'TEAM'
export type FenceType = 'OPERATION' | 'NO_RIDE' | 'NO_PARK'
export type RevenueGranularity = 'DAY' | 'MONTH'

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

export type CoordinateSystem = 'WGS84' | 'GCJ02'

export type LifecycleStatus =
  | 'PENDING'
  | 'OPERATING'
  | 'MAINTENANCE'
  | 'DISPATCHING'
  | 'RETIRED'
  | 'IMPOUNDED'

export type LockStatus = 'LOCKED' | 'UNLOCKED' | 'UNKNOWN'
export type RideStatus = 'IDLE' | 'RIDING' | 'DISPATCHING' | 'MAINTENANCE'
export type ControllerStatus = 'NORMAL' | 'FAULT' | 'OFFLINE'

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export interface LatestState {
  reportedAt: string
  longitude: number
  latitude: number
  accuracyMeters: number | null
  speedKmh: number | null
  directionDegrees: number | null
  satelliteCount: number | null
  batteryPercent: number | null
  remainingRangeKm: number | null
  lockStatus: LockStatus
  rideStatus: RideStatus
  controllerStatus: ControllerStatus
  online: boolean
  signalStrength: number | null
  faultCodes: string[]
  coordinateSystem: CoordinateSystem
}

export interface VehicleAsset {
  vehicleId: string
  companyId: string
  lockId: string
  controllerId: string
  plateNumber: string | null
  filingCode: string | null
  model: string
  batchNo: string | null
  operationCityCode: string
  operationAreaCode: string
  launchDate: string
  lifecycleStatus: LifecycleStatus
}

export interface VehicleListItem {
  vehicleId: string
  plateNumber: string | null
  filingCode: string | null
  model: string
  operationCityCode: string
  operationAreaCode: string
  lifecycleStatus: LifecycleStatus
  latestState: LatestState | null
}

export interface VehicleDetail {
  asset: VehicleAsset
  latestState: LatestState | null
}

export interface PageData<T> {
  items: T[]
  total: number
  page: number
  pageSize: number
}

export interface VehicleQuery {
  page: number
  pageSize: number
  keyword?: string
  cityCode?: string
  lifecycleStatus?: LifecycleStatus
}

export interface MapMarker {
  markerType: 'VEHICLE' | 'CLUSTER'
  markerId: string
  vehicleId: string | null
  longitude: number
  latitude: number
  vehicleCount: number
  lowBatteryCount: number
  faultCount: number
  batteryPercent: number | null
  lifecycleStatus: LifecycleStatus | null
  latestState: LatestState | null
}

export interface MapResult {
  markers: MapMarker[]
  clustered: boolean
  coordinateSystem: CoordinateSystem
}

export interface MapQuery {
  minLongitude: number
  minLatitude: number
  maxLongitude: number
  maxLatitude: number
  zoom: number
  online?: boolean
  lifecycleStatus?: LifecycleStatus
  coordinateSystem: CoordinateSystem
}

export interface TrajectoryPoint {
  reportedAt: string
  longitude: number
  latitude: number
  accuracyMeters: number | null
  speedKmh: number | null
  directionDegrees: number | null
  batteryPercent: number | null
  lockStatus: LockStatus
  rideStatus: RideStatus
  coordinateSystem: CoordinateSystem
}

export interface TrajectoryResult {
  points: TrajectoryPoint[]
  truncated: boolean
  coordinateSystem: CoordinateSystem
}

export interface CityDefinition {
  code: string
  name: string
  orgId: string
  orgName: string
  center: [number, number]
  bounds: [number, number, number, number]
}

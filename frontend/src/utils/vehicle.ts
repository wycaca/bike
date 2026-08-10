import type {
  CityDefinition,
  ControllerStatus,
  LatestState,
  LifecycleStatus,
  LockStatus,
  MapMarker,
  RideStatus,
  TrajectoryPoint,
} from '@/types/vehicle'

export const CITIES: CityDefinition[] = [
  {
    code: '110000',
    name: '北京',
    center: [116.4074, 39.9042],
    bounds: [116.2, 39.8, 116.6, 40.1],
  },
  {
    code: '310000',
    name: '上海',
    center: [121.4737, 31.2304],
    bounds: [121.3, 31.1, 121.7, 31.4],
  },
]

export const lifecycleLabels: Record<LifecycleStatus, string> = {
  PENDING: '待投放',
  OPERATING: '运营中',
  MAINTENANCE: '维修中',
  DISPATCHING: '调度中',
  RETIRED: '已退役',
  IMPOUNDED: '已扣留',
}

export const lockLabels: Record<LockStatus, string> = {
  LOCKED: '已锁车',
  UNLOCKED: '已开锁',
  UNKNOWN: '锁状态未知',
}

export const rideLabels: Record<RideStatus, string> = {
  IDLE: '空闲',
  RIDING: '骑行中',
  DISPATCHING: '调度中',
  MAINTENANCE: '维修中',
}

export const controllerLabels: Record<ControllerStatus, string> = {
  NORMAL: '控制器正常',
  FAULT: '控制器故障',
  OFFLINE: '控制器离线',
}

export function cityName(code: string): string {
  return CITIES.find((city) => city.code === code)?.name ?? code
}

export function formatTime(value?: string | null): string {
  if (!value) return '--'
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(new Date(value))
}

export function vehicleCondition(state: LatestState | null) {
  if (!state || !state.online || state.controllerStatus === 'OFFLINE') {
    return { key: 'offline', label: '离线', color: '#737b78' }
  }
  if (state.faultCodes.length > 0 || state.controllerStatus === 'FAULT') {
    return { key: 'fault', label: '故障', color: '#d34444' }
  }
  if (state.batteryPercent !== null && state.batteryPercent <= 20) {
    return { key: 'low-battery', label: '低电量', color: '#d68a17' }
  }
  if (state.rideStatus === 'RIDING') {
    return { key: 'riding', label: '骑行中', color: '#236fb5' }
  }
  return { key: 'normal', label: '正常', color: '#198754' }
}

export function markerCondition(marker: MapMarker) {
  if (marker.markerType === 'CLUSTER') {
    if (marker.faultCount > 0) return { key: 'fault', color: '#d34444' }
    if (marker.lowBatteryCount > 0) return { key: 'low-battery', color: '#d68a17' }
    return { key: 'normal', color: '#198754' }
  }
  return vehicleCondition(marker.latestState)
}

export function projectCoordinate(
  longitude: number,
  latitude: number,
  bounds: CityDefinition['bounds'],
) {
  const [minLongitude, minLatitude, maxLongitude, maxLatitude] = bounds
  const left = ((longitude - minLongitude) / (maxLongitude - minLongitude)) * 100
  const top = ((maxLatitude - latitude) / (maxLatitude - minLatitude)) * 100
  return {
    left: Math.min(96, Math.max(4, left)),
    top: Math.min(94, Math.max(6, top)),
  }
}

export function trajectoryDistanceKm(points: TrajectoryPoint[]): number {
  const earthRadiusKm = 6371
  let distanceKm = 0

  for (let index = 1; index < points.length; index += 1) {
    const previous = points[index - 1]!
    const current = points[index]!
    const latitudeDelta = ((current.latitude - previous.latitude) * Math.PI) / 180
    const longitudeDelta = ((current.longitude - previous.longitude) * Math.PI) / 180
    const previousLatitude = (previous.latitude * Math.PI) / 180
    const currentLatitude = (current.latitude * Math.PI) / 180
    const haversine =
      Math.sin(latitudeDelta / 2) ** 2 +
      Math.cos(previousLatitude) *
        Math.cos(currentLatitude) *
        Math.sin(longitudeDelta / 2) ** 2

    distanceKm += 2 * earthRadiusKm * Math.asin(Math.sqrt(haversine))
  }

  return distanceKm
}

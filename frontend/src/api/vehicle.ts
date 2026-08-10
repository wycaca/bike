import { http } from './http'

import type {
  ApiResponse,
  MapQuery,
  MapResult,
  PageData,
  TrajectoryResult,
  VehicleDetail,
  VehicleListItem,
  VehicleQuery,
} from '@/types/vehicle'

export async function getVehicles(query: VehicleQuery, signal?: AbortSignal) {
  const response = await http.get<ApiResponse<PageData<VehicleListItem>>>('/v1/vehicles', {
    params: query,
    signal,
  })
  return response.data.data
}

export async function getVehicle(vehicleId: string, signal?: AbortSignal) {
  const response = await http.get<ApiResponse<VehicleDetail>>(`/v1/vehicles/${vehicleId}`, {
    signal,
  })
  return response.data.data
}

export async function getMapVehicles(query: MapQuery, signal?: AbortSignal) {
  const response = await http.get<ApiResponse<MapResult>>('/v1/map/vehicles', {
    params: query,
    signal,
  })
  return response.data.data
}

export async function getTrajectory(
  vehicleId: string,
  startTime: string,
  endTime: string,
  signal?: AbortSignal,
) {
  const response = await http.get<ApiResponse<TrajectoryResult>>(
    `/v1/vehicles/${vehicleId}/trajectory`,
    { params: { startTime, endTime, coordinateSystem: 'GCJ02' }, signal },
  )
  return response.data.data
}

import { http } from '@/api/http'
import type {
  Geofence,
  GeofenceRequest,
  GeoOverview,
  ParkingPoint,
  ParkingPointRequest,
} from '@/types/operations'
import type { ApiResponse } from '@/types/vehicle'

/** 输入: 城市代码; 输出: 围栏、停车点及实时违规。 */
export async function getGeoOverview(cityCode: string): Promise<GeoOverview> {
  const response = await http.get<ApiResponse<GeoOverview>>('/v1/geo/overview', {
    params: { cityCode },
  })
  return response.data.data
}

/** 输入: 围栏内容和可选编号; 输出: 新建或更新后的围栏。 */
export async function saveFence(request: GeofenceRequest, fenceId?: string): Promise<Geofence> {
  const response = fenceId
    ? await http.put<ApiResponse<Geofence>>(`/v1/geo/fences/${fenceId}`, request)
    : await http.post<ApiResponse<Geofence>>('/v1/geo/fences', request)
  return response.data.data
}

/** 输入: 围栏编号; 输出: 无, 停用围栏。 */
export async function disableFence(fenceId: string): Promise<void> {
  await http.delete(`/v1/geo/fences/${fenceId}`)
}

/** 输入: 停车点内容和可选编号; 输出: 新建或更新后的停车点。 */
export async function saveParkingPoint(
  request: ParkingPointRequest,
  pointId?: string,
): Promise<ParkingPoint> {
  const response = pointId
    ? await http.put<ApiResponse<ParkingPoint>>(`/v1/geo/parking-points/${pointId}`, request)
    : await http.post<ApiResponse<ParkingPoint>>('/v1/geo/parking-points', request)
  return response.data.data
}

/** 输入: 停车点编号; 输出: 无, 停用停车点。 */
export async function disableParkingPoint(pointId: string): Promise<void> {
  await http.delete(`/v1/geo/parking-points/${pointId}`)
}

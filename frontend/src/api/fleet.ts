import { http } from '@/api/http'
import type { ApiResponse, VehicleBatchResult, VehicleCreateRequest } from '@/types/vehicle'

/** 输入: 单辆车辆档案; 输出: 新增车辆编号。 */
export async function createVehicle(request: VehicleCreateRequest): Promise<string> {
  const response = await http.post<ApiResponse<{ vehicleId: string }>>('/v1/admin/vehicles', request)
  return response.data.data.vehicleId
}

/** 输入: 最多 500 辆车辆档案; 输出: 成功数量和逐行跳过原因。 */
export async function createVehiclesBatch(vehicles: VehicleCreateRequest[]): Promise<VehicleBatchResult> {
  const response = await http.post<ApiResponse<VehicleBatchResult>>('/v1/admin/vehicles/batch', { vehicles })
  return response.data.data
}

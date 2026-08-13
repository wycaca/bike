import { http } from '@/api/http'
import type { AdminCity, ApiResponse, CityDefinition, CityRequest } from '@/types/vehicle'

interface CityResponse {
  cityCode: string
  cityName: string
  orgId: string
  orgName: string
  centerLongitude: number
  centerLatitude: number
  minLongitude: number
  minLatitude: number
  maxLongitude: number
  maxLatitude: number
}

/** 输入: 当前登录会话; 输出: 用户数据范围内的启用城市及地图参数。 */
export async function getCities(): Promise<CityDefinition[]> {
  const response = await http.get<ApiResponse<CityResponse[]>>('/v1/cities')
  return response.data.data.map((city) => ({
    code: city.cityCode,
    name: city.cityName,
    orgId: city.orgId,
    orgName: city.orgName,
    center: [city.centerLongitude, city.centerLatitude],
    bounds: [city.minLongitude, city.minLatitude, city.maxLongitude, city.maxLatitude],
  }))
}

/** 输入: 全量管理员会话; 输出: 包含停用项的全部城市配置。 */
export async function getAdminCities(): Promise<AdminCity[]> {
  const response = await http.get<ApiResponse<Array<CityResponse & {
    status: 'ACTIVE' | 'DISABLED'
    createdAt: string
    updatedAt: string
  }>>>('/v1/admin/cities')
  return response.data.data.map((city) => ({
    code: city.cityCode,
    name: city.cityName,
    orgId: city.orgId,
    orgName: city.orgName,
    center: [city.centerLongitude, city.centerLatitude],
    bounds: [city.minLongitude, city.minLatitude, city.maxLongitude, city.maxLatitude],
    status: city.status,
    createdAt: city.createdAt,
    updatedAt: city.updatedAt,
  }))
}

/** 输入: 城市配置和可选原城市代码; 输出: 新建或更新后的城市配置。 */
export async function saveCity(request: CityRequest, cityCode?: string): Promise<void> {
  if (cityCode) await http.put(`/v1/admin/cities/${cityCode}`, request)
  else await http.post('/v1/admin/cities', request)
}

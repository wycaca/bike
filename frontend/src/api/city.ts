import { http } from '@/api/http'
import type { ApiResponse, CityDefinition } from '@/types/vehicle'

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

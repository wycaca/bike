import { http } from '@/api/http'
import type { DashboardData } from '@/types/operations'
import type { ApiResponse } from '@/types/vehicle'

/** 输入: 城市和趋势天数; 输出: 看板聚合数据。 */
export async function getDashboard(cityCode: string, days: number): Promise<DashboardData> {
  const response = await http.get<ApiResponse<DashboardData>>('/v1/dashboard', {
    params: { cityCode, days },
  })
  return response.data.data
}

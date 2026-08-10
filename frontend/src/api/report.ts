import { http } from '@/api/http'
import type { RevenueGranularity, RevenueReport } from '@/types/operations'
import type { ApiResponse } from '@/types/vehicle'

export interface RevenueReportQuery {
  cityCode: string
  fromDate: string
  toDate: string
  granularity: RevenueGranularity
}

/** 输入: 城市、日期范围和统计粒度; 输出: 收入及单位经济报表。 */
export async function getRevenueReport(query: RevenueReportQuery): Promise<RevenueReport> {
  const response = await http.get<ApiResponse<RevenueReport>>('/v1/reports/revenue', { params: query })
  return response.data.data
}

/** 输入: 收入报表筛选条件; 输出: UTF-8 CSV 二进制。 */
export async function downloadRevenueReport(query: RevenueReportQuery): Promise<Blob> {
  const response = await http.get('/v1/reports/revenue.csv', {
    params: query,
    responseType: 'blob',
  })
  return response.data
}

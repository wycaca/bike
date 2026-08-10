import { http } from '@/api/http'
import type { ReportExportJob, RevenueGranularity, RevenueReport } from '@/types/operations'
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

/** 输入: 收入报表筛选条件; 输出: 独立 Worker 等待处理的导出任务。 */
export async function createRevenueExport(query: RevenueReportQuery): Promise<ReportExportJob> {
  const response = await http.post<ApiResponse<ReportExportJob>>('/v1/reports/exports', {
    reportType: 'REVENUE',
    ...query,
  })
  return response.data.data
}

/** 输入: 导出任务编号; 输出: 最新任务状态。 */
export async function getReportExport(jobId: string): Promise<ReportExportJob> {
  const response = await http.get<ApiResponse<ReportExportJob>>(`/v1/reports/exports/${jobId}`)
  return response.data.data
}

/** 输入: 已完成任务编号; 输出: Worker 生成的 CSV 二进制。 */
export async function downloadReportExport(jobId: string): Promise<Blob> {
  const response = await http.get(`/v1/reports/exports/${jobId}/file`, { responseType: 'blob' })
  return response.data
}

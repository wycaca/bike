import { http } from './http'

import type {
  OperationsAssignee,
  OperationsAttachment,
  OperationsAttachmentPurpose,
  OperationsBatchResult,
  OperationsBatchTaskRequest,
  OperationsCompletionRequest,
  OperationsExceptionType,
  OperationsRoutePlan,
  OperationsRule,
  OperationsRuleRequest,
  OperationsTaskDetail,
  OperationsTaskRequest,
  OperationsTaskScope,
  OperationsTaskStatus,
  OperationsTaskSummary,
  OperationsTaskType,
  PagedData,
} from '@/types/operations'
import type { ApiResponse } from '@/types/vehicle'

export interface OperationsTaskQuery {
  cityCode: string
  status?: OperationsTaskStatus
  type?: OperationsTaskType
  scope: OperationsTaskScope
  keyword?: string
  page: number
  pageSize: number
}

/** 输入: 运维任务筛选; 输出: 运维任务分页。 */
export async function getOperationsTasks(query: OperationsTaskQuery) {
  const response = await http.get<ApiResponse<PagedData<OperationsTaskDetail['task']>>>('/v1/ops/tasks', {
    params: query,
  })
  return response.data.data
}

/** 输入: 城市代码; 输出: 运维任务汇总。 */
export async function getOperationsSummary(cityCode: string) {
  const response = await http.get<ApiResponse<OperationsTaskSummary>>('/v1/ops/tasks/summary', {
    params: { cityCode },
  })
  return response.data.data
}

/** 输入: 城市代码; 输出: 可指派运维人员。 */
export async function getOperationsAssignees(cityCode: string) {
  const response = await http.get<ApiResponse<OperationsAssignee[]>>('/v1/ops/tasks/assignees', {
    params: { cityCode },
  })
  return response.data.data
}

/** 输入: 任务编号; 输出: 任务详情与操作时间线。 */
export async function getOperationsTask(taskId: string) {
  const response = await http.get<ApiResponse<OperationsTaskDetail>>(`/v1/ops/tasks/${taskId}`)
  return response.data.data
}

/** 输入: 新任务; 输出: 创建后的任务详情。 */
export async function createOperationsTask(request: OperationsTaskRequest) {
  const response = await http.post<ApiResponse<OperationsTaskDetail>>('/v1/ops/tasks', request)
  return response.data.data
}

/** 输入: 批量任务模板和车辆编号; 输出: 成功任务及逐车跳过原因。 */
export async function createOperationsBatch(request: OperationsBatchTaskRequest) {
  const response = await http.post<ApiResponse<OperationsBatchResult>>('/v1/ops/tasks/batch', request)
  return response.data.data
}

/** 输入: 任务编号和动作; 输出: 状态流转后的任务详情。 */
export async function changeOperationsTask(
  taskId: string,
  action: 'claim' | 'release' | 'start',
) {
  const response = await http.post<ApiResponse<OperationsTaskDetail>>(`/v1/ops/tasks/${taskId}/${action}`)
  return response.data.data
}

/** 输入: 任务编号和结果说明; 输出: 完成后的任务详情。 */
export async function completeOperationsTask(taskId: string, request: OperationsCompletionRequest) {
  const response = await http.post<ApiResponse<OperationsTaskDetail>>(`/v1/ops/tasks/${taskId}/complete`, request)
  return response.data.data
}

/** 输入: 任务、用途和图片; 输出: 可被完工或异常请求引用的附件。 */
export async function uploadOperationsAttachment(
  taskId: string,
  purpose: OperationsAttachmentPurpose,
  file: File,
) {
  const form = new FormData()
  form.append('file', file)
  const response = await http.post<ApiResponse<OperationsAttachment>>(
    `/v1/ops/tasks/${taskId}/attachments`, form, { params: { purpose } },
  )
  return response.data.data
}

/** 输入: 任务和现场异常; 输出: 进入异常状态的任务。 */
export async function reportOperationsException(
  taskId: string,
  request: { exceptionType: OperationsExceptionType; note: string; attachmentIds: number[] },
) {
  const response = await http.post<ApiResponse<OperationsTaskDetail>>(`/v1/ops/tasks/${taskId}/exception`, request)
  return response.data.data
}

/** 输入: 异常任务和管理员处理动作; 输出: 重开或关闭后的任务。 */
export async function resolveOperationsException(taskId: string, action: 'REOPEN' | 'CLOSE', note: string) {
  const response = await http.post<ApiResponse<OperationsTaskDetail>>(`/v1/ops/tasks/${taskId}/exception/resolve`, { action, note })
  return response.data.data
}

/** 输入: 待验收任务和结论; 输出: 完成或退回后的任务。 */
export async function reviewOperationsTask(taskId: string, action: 'APPROVE' | 'REJECT', note: string) {
  const response = await http.post<ApiResponse<OperationsTaskDetail>>(`/v1/ops/tasks/${taskId}/review`, { action, note })
  return response.data.data
}

/** 输入: 城市; 输出: 自动任务规则。 */
export async function getOperationsRules(cityCode: string) {
  const response = await http.get<ApiResponse<OperationsRule[]>>('/v1/ops/rules', { params: { cityCode } })
  return response.data.data
}

/** 输入: 规则配置; 输出: 新建规则。 */
export async function createOperationsRule(request: OperationsRuleRequest) {
  const response = await http.post<ApiResponse<OperationsRule>>('/v1/ops/rules', request)
  return response.data.data
}

/** 输入: 规则编号、版本和配置; 输出: 更新规则。 */
export async function updateOperationsRule(ruleId: string, version: number, request: OperationsRuleRequest) {
  const response = await http.put<ApiResponse<OperationsRule>>(`/v1/ops/rules/${ruleId}`, request, { params: { version } })
  return response.data.data
}

/** 输入: 城市; 输出: 手工规则扫描统计。 */
export async function scanOperationsRules(cityCode: string) {
  const response = await http.post<ApiResponse<{ scannedVehicles: number; createdTasks: number; deduplicatedSignals: number }>>('/v1/ops/automation/scan', undefined, { params: { cityCode } })
  return response.data.data
}

/** 输入: 选中任务; 输出: 高德道路矩阵优化后的作业顺序。 */
export async function optimizeOperationsRoute(taskIds: string[]) {
  const response = await http.post<ApiResponse<OperationsRoutePlan>>('/v1/ops/routes/optimize', {
    taskIds, startLongitude: null, startLatitude: null,
  })
  return response.data.data
}

/** 输入: 任务编号和取消原因; 输出: 取消后的任务详情。 */
export async function cancelOperationsTask(taskId: string, reason: string) {
  const response = await http.post<ApiResponse<OperationsTaskDetail>>(`/v1/ops/tasks/${taskId}/cancel`, {
    reason,
  })
  return response.data.data
}

/** 输入: 任务编号和运维人员; 输出: 指派或改派后的任务详情。 */
export async function assignOperationsTask(taskId: string, assigneeId: string) {
  const response = await http.put<ApiResponse<OperationsTaskDetail>>(`/v1/ops/tasks/${taskId}/assignment`, {
    assigneeId,
  })
  return response.data.data
}

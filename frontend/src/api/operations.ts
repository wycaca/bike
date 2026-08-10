import { http } from './http'

import type {
  OperationsAssignee,
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

/** 输入: 任务编号和动作; 输出: 状态流转后的任务详情。 */
export async function changeOperationsTask(
  taskId: string,
  action: 'claim' | 'release' | 'start',
) {
  const response = await http.post<ApiResponse<OperationsTaskDetail>>(`/v1/ops/tasks/${taskId}/${action}`)
  return response.data.data
}

/** 输入: 任务编号和结果说明; 输出: 完成后的任务详情。 */
export async function completeOperationsTask(taskId: string, resultNote: string) {
  const response = await http.post<ApiResponse<OperationsTaskDetail>>(`/v1/ops/tasks/${taskId}/complete`, {
    resultNote,
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

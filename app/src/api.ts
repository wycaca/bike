import axios, { type InternalAxiosRequestConfig } from 'axios'

import type {
  ApiResponse, Assignee, Attachment, BatchCreateResult, CityDefinition, CurrentUser, ExceptionType, MapQuery, MapResult,
  RevenueReport, RoutePlan, TaskDetail, TaskPage, TaskPriority, TaskRule, TaskStatus, TaskSummary, TaskType, Vehicle,
} from '@/types'

export const http = axios.create({
  baseURL: '/api/v1', timeout: 12_000, withCredentials: true,
  xsrfCookieName: 'XSRF-TOKEN', xsrfHeaderName: 'X-XSRF-TOKEN',
})

interface RetryRequestConfig extends InternalAxiosRequestConfig {
  retryCount?: number
}

const RETRYABLE_STATUS = new Set([408, 429, 502, 503, 504])
const MAX_RETRIES = 2

/** 输入: Axios 错误; 输出: 是否为可安全重试的幂等临时故障。 */
export function shouldRetryRequest(error: unknown): boolean {
  if (!axios.isAxiosError(error) || !error.config || error.code === 'ERR_CANCELED') return false
  const config = error.config as RetryRequestConfig
  const method = config.method?.toUpperCase()
  if (!['GET', 'HEAD'].includes(method ?? '') || (config.retryCount ?? 0) >= MAX_RETRIES) return false
  return !error.response || RETRYABLE_STATUS.has(error.response.status)
}

/** 输入: 已重试次数; 输出: 短指数退避完成后的 Promise。 */
function retryDelay(retryCount: number): Promise<void> {
  return new Promise((resolve) => window.setTimeout(resolve, 250 * 2 ** retryCount))
}

http.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResponse<unknown>
    if (typeof body?.code === 'number' && body.code !== 0) throw new Error(body.message || '请求失败')
    return response
  },
  async (error) => {
    if (shouldRetryRequest(error)) {
      const config = error.config as RetryRequestConfig
      const retryCount = config.retryCount ?? 0
      config.retryCount = retryCount + 1
      await retryDelay(retryCount)
      return http.request(config)
    }
    if (axios.isAxiosError(error) && error.response?.status === 401 && typeof window !== 'undefined') {
      window.dispatchEvent(new Event('auth-expired'))
    }
    return Promise.reject(error)
  },
)

let csrfToken = ''

/** 输入: 无; 输出: 获取并缓存写请求使用的 CSRF 令牌。 */
export async function refreshCsrf() {
  const response = await http.get<ApiResponse<{ token: string }>>('/auth/csrf')
  csrfToken = response.data.data.token
  return csrfToken
}

/** 输入: Axios 写请求配置; 输出: 附带当前 CSRF 令牌的请求头。 */
async function writeHeaders() {
  if (!csrfToken) await refreshCsrf()
  return { 'X-XSRF-TOKEN': csrfToken }
}

export async function login(username: string, password: string) {
  await refreshCsrf()
  const body = new URLSearchParams({ username, password })
  const response = await http.post<ApiResponse<CurrentUser>>('/auth/login', body, {
    headers: { ...(await writeHeaders()), 'Content-Type': 'application/x-www-form-urlencoded' },
  })
  await refreshCsrf()
  return response.data.data
}

export async function currentUser() {
  return (await http.get<ApiResponse<CurrentUser>>('/auth/me')).data.data
}

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

/** 输入: 当前登录会话; 输出: 用户数据权限内的启用城市与地图参数。 */
export async function getCities(): Promise<CityDefinition[]> {
  const response = await http.get<ApiResponse<CityResponse[]>>('/cities')
  return response.data.data.map((city) => ({
    code: city.cityCode,
    name: city.cityName,
    orgId: city.orgId,
    orgName: city.orgName,
    center: [city.centerLongitude, city.centerLatitude],
    bounds: [city.minLongitude, city.minLatitude, city.maxLongitude, city.maxLatitude],
  }))
}

export async function logout() {
  await http.post('/auth/logout', undefined, { headers: await writeHeaders() })
  csrfToken = ''
}

export async function getTaskSummary(cityCode: string) {
  return (await http.get<ApiResponse<TaskSummary>>('/ops/tasks/summary', { params: { cityCode } })).data.data
}

/** 输入: 城市和完整自然日区间; 输出: 收入、订单、车辆周转和单位经济指标。 */
export async function getRevenueReport(params: { cityCode: string; fromDate: string; toDate: string; granularity: 'DAY' | 'MONTH' }) {
  return (await http.get<ApiResponse<RevenueReport>>('/reports/revenue', { params })).data.data
}

export async function getTasks(params: { cityCode: string; scope: 'ALL' | 'MINE' | 'UNASSIGNED'; status?: TaskStatus; type?: TaskType; keyword?: string }) {
  return (await http.get<ApiResponse<TaskPage>>('/ops/tasks', { params: { ...params, page: 1, pageSize: 100 } })).data.data
}

export async function getTask(taskId: string) {
  return (await http.get<ApiResponse<TaskDetail>>(`/ops/tasks/${taskId}`)).data.data
}

export async function taskAction(taskId: string, action: 'claim' | 'release' | 'start') {
  return (await http.post<ApiResponse<TaskDetail>>(`/ops/tasks/${taskId}/${action}`, undefined, { headers: await writeHeaders() })).data.data
}

/** 输入: 城市编码; 输出: 当前城市可被指派任务的运维人员。 */
export async function getAssignees(cityCode: string) {
  return (await http.get<ApiResponse<Assignee[]>>('/ops/tasks/assignees', { params: { cityCode } })).data.data
}

/** 输入: 任务与运维人员编号; 输出: 指派后的任务详情。 */
export async function assignTask(taskId: string, assigneeId: string) {
  return (await http.put<ApiResponse<TaskDetail>>(`/ops/tasks/${taskId}/assignment`, { assigneeId }, {
    headers: await writeHeaders(),
  })).data.data
}

export async function reviewTask(taskId: string, action: 'APPROVE' | 'REJECT', note: string) {
  return (await http.post<ApiResponse<TaskDetail>>(`/ops/tasks/${taskId}/review`, { action, note }, { headers: await writeHeaders() })).data.data
}

export async function resolveException(taskId: string, action: 'REOPEN' | 'CLOSE', note: string) {
  return (await http.post<ApiResponse<TaskDetail>>(`/ops/tasks/${taskId}/exception/resolve`, { action, note }, { headers: await writeHeaders() })).data.data
}

export async function reportException(taskId: string, exceptionType: ExceptionType, note: string, attachmentIds: number[]) {
  return (await http.post<ApiResponse<TaskDetail>>(`/ops/tasks/${taskId}/exception`, { exceptionType, note, attachmentIds }, { headers: await writeHeaders() })).data.data
}

export async function uploadAttachment(taskId: string, purpose: 'BEFORE' | 'AFTER' | 'EXCEPTION', file: File) {
  const form = new FormData()
  form.append('file', file)
  return (await http.post<ApiResponse<Attachment>>(`/ops/tasks/${taskId}/attachments`, form, {
    params: { purpose }, headers: await writeHeaders(),
  })).data.data
}

export async function completeTask(taskId: string, request: Record<string, unknown>) {
  return (await http.post<ApiResponse<TaskDetail>>(`/ops/tasks/${taskId}/complete`, request, { headers: await writeHeaders() })).data.data
}

export async function optimizeRoute(taskIds: string[], start?: { longitude: number; latitude: number }) {
  return (await http.post<ApiResponse<RoutePlan>>('/ops/routes/optimize', {
    taskIds, startLongitude: start?.longitude ?? null, startLatitude: start?.latitude ?? null,
  }, { headers: await writeHeaders() })).data.data
}

export async function getRules(cityCode: string) {
  return (await http.get<ApiResponse<TaskRule[]>>('/ops/rules', { params: { cityCode } })).data.data
}

export async function updateRule(rule: TaskRule) {
  const { ruleId: _ruleId, version: _version, ...request } = rule
  return (await http.put<ApiResponse<TaskRule>>(`/ops/rules/${rule.ruleId}`, request, {
    params: { version: rule.version }, headers: await writeHeaders(),
  })).data.data
}

/** 输入: 批量任务模板和车辆编号; 输出: 创建成功及被去重跳过的车辆。 */
export async function createBatch(request: {
  batchName: string
  taskType: TaskType
  priority: TaskPriority
  title: string
  description: string
  vehicleIds: string[]
  orgId: string
  targetName?: string
  dueAt?: string
  assigneeId?: string
}) {
  return (await http.post<ApiResponse<BatchCreateResult>>('/ops/tasks/batch', request, {
    headers: await writeHeaders(),
  })).data.data
}

export async function scanRules(cityCode: string) {
  return (await http.post<ApiResponse<{ scannedVehicles: number; createdTasks: number; deduplicatedSignals: number }>>('/ops/automation/scan', undefined, {
    params: { cityCode }, headers: await writeHeaders(),
  })).data.data
}

export async function getVehicles(cityCode: string, keyword = '') {
  return (await http.get<ApiResponse<{ items: Vehicle[] }>>('/vehicles', {
    params: { cityCode, keyword: keyword || undefined, page: 1, pageSize: 50 },
  })).data.data.items
}

/** 输入: 地图视野、缩放级别和在线筛选; 输出: GCJ02 坐标的车辆或聚合标记。 */
export async function getMapVehicles(query: MapQuery, signal?: AbortSignal) {
  return (await http.get<ApiResponse<MapResult>>('/map/vehicles', { params: query, signal })).data.data
}

export function errorText(error: unknown) {
  if (axios.isAxiosError(error)) {
    if (error.response?.data?.message) return error.response.data.message
    if (error.code === 'ECONNABORTED') return '请求超时，已自动重试，请稍后再试'
    if (!error.response) return typeof navigator !== 'undefined' && !navigator.onLine
      ? '网络已断开，请检查网络连接'
      : '无法连接服务，已自动重试，请稍后再试'
    if (error.response.status === 429) return '请求过于频繁，请稍后再试'
    if (error.response.status >= 500) return '服务暂时不可用，请稍后再试'
    return error.message || '网络请求失败'
  }
  return error instanceof Error ? error.message : '操作失败'
}

import axios, { type InternalAxiosRequestConfig } from 'axios'

import type { ApiResponse } from '@/types/vehicle'

export const http = axios.create({
  baseURL: '/api',
  timeout: 10_000,
  withCredentials: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
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

/** 输入: 最终 HTTP 错误; 输出: 通知应用统一处理失效会话。 */
function notifyExpiredSession(error: unknown): void {
  if (axios.isAxiosError(error) && error.response?.status === 401 && typeof window !== 'undefined') {
    window.dispatchEvent(new Event('auth-expired'))
  }
}

http.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResponse<unknown>
    if (typeof body?.code === 'number' && body.code !== 0) {
      return Promise.reject(new Error(body.message || '请求失败'))
    }
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
    notifyExpiredSession(error)
    return Promise.reject(error)
  },
)

export function errorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    if (error.code === 'ERR_CANCELED') return ''
    if (error.response?.data?.message) return error.response.data.message
    if (error.code === 'ECONNABORTED') return '请求超时，已自动重试，请稍后再试'
    if (!error.response) return typeof navigator !== 'undefined' && !navigator.onLine
      ? '网络已断开，请检查网络连接'
      : '无法连接服务，已自动重试，请稍后再试'
    if (error.response.status === 429) return '请求过于频繁，请稍后再试'
    if (error.response.status >= 500) return '服务暂时不可用，请稍后再试'
    return error.message || '网络请求失败'
  }
  return error instanceof Error ? error.message : '发生未知错误'
}

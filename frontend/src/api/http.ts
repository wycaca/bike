import axios from 'axios'

import type { ApiResponse } from '@/types/vehicle'

export const http = axios.create({
  baseURL: '/api',
  timeout: 10_000,
  withCredentials: true,
})

http.interceptors.response.use((response) => {
  const body = response.data as ApiResponse<unknown>
  if (typeof body?.code === 'number' && body.code !== 0) {
    return Promise.reject(new Error(body.message || '请求失败'))
  }
  return response
})

export function errorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    if (error.code === 'ERR_CANCELED') return ''
    return error.response?.data?.message || error.message || '网络请求失败'
  }
  return error instanceof Error ? error.message : '发生未知错误'
}

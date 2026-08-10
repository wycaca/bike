import { http } from '@/api/http'
import type { CurrentUser } from '@/types/operations'
import type { ApiResponse } from '@/types/vehicle'

/** 输入: 无; 输出: 当前会话 CSRF 令牌并触发 Cookie 写入。 */
export async function ensureCsrf(): Promise<string> {
  const response = await http.get<ApiResponse<{ token: string }>>('/v1/auth/csrf')
  const token = response.data.data.token
  http.defaults.headers.common['X-XSRF-TOKEN'] = token
  return token
}

/** 输入: 用户名和密码; 输出: 已登录用户。 */
export async function login(username: string, password: string): Promise<CurrentUser> {
  await ensureCsrf()
  const body = new URLSearchParams({ username, password })
  const response = await http.post<ApiResponse<CurrentUser>>('/v1/auth/login', body, {
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  })
  // Spring Security 在登录成功后轮换令牌，后续写请求必须使用新令牌。
  await ensureCsrf()
  return response.data.data
}

/** 输入: 无; 输出: 当前登录用户。 */
export async function getCurrentUser(): Promise<CurrentUser> {
  const response = await http.get<ApiResponse<CurrentUser>>('/v1/auth/me')
  await ensureCsrf()
  return response.data.data
}

/** 输入: 无; 输出: 无, 注销当前会话。 */
export async function logout(): Promise<void> {
  await ensureCsrf()
  await http.post('/v1/auth/logout')
}

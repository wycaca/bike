import { http } from '@/api/http'
import type {
  AuditLog,
  Organization,
  OrganizationRequest,
  PagedData,
  PlatformUser,
  UserRequest,
} from '@/types/operations'
import type { ApiResponse } from '@/types/vehicle'

/** 输入: 无; 输出: 全部组织。 */
export async function getOrganizations(): Promise<Organization[]> {
  const response = await http.get<ApiResponse<Organization[]>>('/v1/admin/organizations')
  return response.data.data
}

/** 输入: 组织内容和可选编号; 输出: 新建或更新后的组织。 */
export async function saveOrganization(request: OrganizationRequest, orgId?: string): Promise<Organization> {
  const response = orgId
    ? await http.put<ApiResponse<Organization>>(`/v1/admin/organizations/${orgId}`, request)
    : await http.post<ApiResponse<Organization>>('/v1/admin/organizations', request)
  return response.data.data
}

/** 输入: 用户分页条件; 输出: 用户分页。 */
export async function getUsers(page: number, pageSize: number, keyword: string): Promise<PagedData<PlatformUser>> {
  const response = await http.get<ApiResponse<PagedData<PlatformUser>>>('/v1/admin/users', {
    params: { page, pageSize, keyword: keyword || undefined },
  })
  return response.data.data
}

/** 输入: 用户内容和可选编号; 输出: 新建或更新后的用户。 */
export async function saveUser(request: UserRequest, userId?: string): Promise<PlatformUser> {
  const response = userId
    ? await http.put<ApiResponse<PlatformUser>>(`/v1/admin/users/${userId}`, request)
    : await http.post<ApiResponse<PlatformUser>>('/v1/admin/users', request)
  return response.data.data
}

/** 输入: 用户编号和新密码; 输出: 无。 */
export async function resetUserPassword(userId: string, password: string): Promise<void> {
  await http.put(`/v1/admin/users/${userId}/password`, { password })
}

/** 输入: 审计筛选与分页; 输出: 审计日志分页。 */
export async function getAuditLogs(
  page: number,
  pageSize: number,
  keyword: string,
  action: string,
): Promise<PagedData<AuditLog>> {
  const response = await http.get<ApiResponse<PagedData<AuditLog>>>('/v1/admin/audit-logs', {
    params: { page, pageSize, keyword: keyword || undefined, action: action || undefined },
  })
  return response.data.data
}

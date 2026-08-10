import type { Organization, UserRole } from '@/types/operations'

export const roleLabels: Record<UserRole, string> = {
  ADMIN: '系统管理员',
  OPERATOR: '运营人员',
  AUDITOR: '审计人员',
}

export const actionLabels: Record<string, string> = {
  CREATE: '新建',
  UPDATE: '更新',
  DELETE: '停用',
  ACCESS: '访问',
}

/** 输入: 组织编号和组织列表; 输出: 从当前组织到根组织的显示路径。 */
export function organizationPath(orgId: string | null, organizations: Organization[]): string {
  if (!orgId) return '--'
  const byId = new Map(organizations.map((org) => [org.orgId, org]))
  const names: string[] = []
  const visited = new Set<string>()
  let current = byId.get(orgId)
  while (current && !visited.has(current.orgId)) {
    visited.add(current.orgId)
    names.unshift(current.orgName)
    current = current.parentOrgId ? byId.get(current.parentOrgId) : undefined
  }
  return names.length ? names.join(' / ') : orgId
}

/** 输入: ISO 时间; 输出: 便于审计表展示的本地日期时间。 */
export function auditTime(value: string | null): string {
  if (!value) return '--'
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false,
  }).format(new Date(value))
}

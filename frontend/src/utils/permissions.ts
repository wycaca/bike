import type { DataScope, UserRole } from '@/types/operations'

export type Capability = 'REPORT_READ' | 'GEO_READ' | 'GEO_WRITE' | 'OPS_READ' | 'ADMIN_READ' | 'FLEET_WRITE'

const CAPABILITY_ROLES: Record<Capability, readonly UserRole[]> = {
  REPORT_READ: ['ADMIN', 'AUDITOR'],
  GEO_READ: ['ADMIN', 'OPERATOR'],
  GEO_WRITE: ['ADMIN'],
  OPS_READ: ['ADMIN', 'OPERATOR', 'AUDITOR'],
  ADMIN_READ: ['ADMIN', 'AUDITOR'],
  FLEET_WRITE: ['ADMIN'],
}

/** 输入: 当前角色、平台能力和数据范围; 输出: 当前用户是否可以使用对应功能。 */
export function hasCapability(
  role: UserRole | undefined,
  capability: Capability,
  dataScope?: DataScope,
): boolean {
  if (capability === 'FLEET_WRITE') return role === 'ADMIN' && dataScope === 'ALL'
  return role !== undefined && CAPABILITY_ROLES[capability].includes(role)
}

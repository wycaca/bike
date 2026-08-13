import type { UserRole } from '@/types/operations'

export type Capability = 'REPORT_READ' | 'GEO_READ' | 'GEO_WRITE' | 'OPS_READ' | 'ADMIN_READ'

const CAPABILITY_ROLES: Record<Capability, readonly UserRole[]> = {
  REPORT_READ: ['ADMIN', 'AUDITOR'],
  GEO_READ: ['ADMIN', 'OPERATOR'],
  GEO_WRITE: ['ADMIN'],
  OPS_READ: ['ADMIN', 'OPERATOR', 'AUDITOR'],
  ADMIN_READ: ['ADMIN', 'AUDITOR'],
}

/** 输入: 当前角色和平台能力; 输出: 该角色是否可以使用对应功能。 */
export function hasCapability(role: UserRole | undefined, capability: Capability): boolean {
  return role !== undefined && CAPABILITY_ROLES[capability].includes(role)
}

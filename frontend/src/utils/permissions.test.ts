import { describe, expect, it } from 'vitest'

import { hasCapability } from '@/utils/permissions'

describe('前端权限矩阵', () => {
  it('与后端报表、围栏和审计权限保持一致', () => {
    expect(hasCapability('OPERATOR', 'REPORT_READ')).toBe(false)
    expect(hasCapability('AUDITOR', 'REPORT_READ')).toBe(true)
    expect(hasCapability('OPERATOR', 'GEO_READ')).toBe(true)
    expect(hasCapability('OPERATOR', 'GEO_WRITE')).toBe(false)
    expect(hasCapability('AUDITOR', 'ADMIN_READ')).toBe(true)
    expect(hasCapability('ADMIN', 'FLEET_WRITE', 'ALL')).toBe(true)
    expect(hasCapability('ADMIN', 'FLEET_WRITE', 'ORG_AND_CHILDREN')).toBe(false)
    expect(hasCapability('AUDITOR', 'FLEET_WRITE', 'ALL')).toBe(false)
  })
})

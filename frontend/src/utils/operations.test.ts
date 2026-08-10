import { describe, expect, it } from 'vitest'

import type { Organization } from '@/types/operations'
import { organizationPath } from '@/utils/operations'

const timestamp = '2026-08-10T00:00:00Z'
const organizations: Organization[] = [
  { orgId: 'HQ', parentOrgId: null, orgName: '运营总部', orgType: 'COMPANY', cityCode: null, status: 'ACTIVE', createdAt: timestamp, updatedAt: timestamp },
  { orgId: 'BJ', parentOrgId: 'HQ', orgName: '北京中心', orgType: 'REGION', cityCode: '110000', status: 'ACTIVE', createdAt: timestamp, updatedAt: timestamp },
  { orgId: 'TEAM', parentOrgId: 'BJ', orgName: '东城班组', orgType: 'TEAM', cityCode: '110000', status: 'ACTIVE', createdAt: timestamp, updatedAt: timestamp },
]

describe('organizationPath', () => {
  it('生成从总部到班组的完整路径', () => {
    expect(organizationPath('TEAM', organizations)).toBe('运营总部 / 北京中心 / 东城班组')
  })

  it('未知组织回退显示原编号', () => {
    expect(organizationPath('UNKNOWN', organizations)).toBe('UNKNOWN')
  })
})

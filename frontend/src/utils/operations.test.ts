import { describe, expect, it } from 'vitest'

import type { OperationsTask, Organization } from '@/types/operations'
import { canOperateTask, isTaskOverdue, organizationPath } from '@/utils/operations'

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

const openTask: OperationsTask = {
  taskId: 'TASK-1', taskNo: 'OPS-1', taskType: 'BATTERY_SWAP', status: 'OPEN', priority: 'URGENT', sourceType: 'MANUAL',
  title: '低电量换电', description: null, vehicleId: 'BIKE-1', plateNumber: null,
  cityCode: '110000', areaCode: '110105', orgId: 'BJ', orgName: '北京中心', targetName: null,
  sourceLongitude: 116.4, sourceLatitude: 39.9, batteryPercent: 8,
  assigneeId: null, assigneeName: null, createdBy: 'ADMIN', createdByName: '管理员',
  ruleId: null, ruleName: null, batchId: null, batchNo: null, triggerKey: null, duplicateCount: 0,
  dueAt: '2026-08-10T01:00:00Z', claimedAt: null, startedAt: null, submittedAt: null, completedAt: null,
  resultNote: null, exceptionType: null, exceptionNote: null, exceptionAt: null,
  version: 0, createdAt: timestamp, updatedAt: timestamp,
}

describe('运维任务操作规则', () => {
  it('运维人员可抢待领取任务，管理员可指派但不能抢单', () => {
    expect(canOperateTask(openTask, 'OPERATOR', 'OP-1', 'claim')).toBe(true)
    expect(canOperateTask(openTask, 'ADMIN', 'ADMIN', 'claim')).toBe(false)
    expect(canOperateTask(openTask, 'ADMIN', 'ADMIN', 'assign')).toBe(true)
  })

  it('只有领取人可开始和完成任务', () => {
    const claimed = { ...openTask, status: 'CLAIMED' as const, assigneeId: 'OP-1' }
    expect(canOperateTask(claimed, 'OPERATOR', 'OP-1', 'start')).toBe(true)
    expect(canOperateTask(claimed, 'OPERATOR', 'OP-2', 'start')).toBe(false)
    expect(canOperateTask({ ...claimed, status: 'IN_PROGRESS' }, 'OPERATOR', 'OP-1', 'complete')).toBe(true)
  })

  it('已完成任务不计为超时', () => {
    const now = new Date('2026-08-10T02:00:00Z')
    expect(isTaskOverdue(openTask, now)).toBe(true)
    expect(isTaskOverdue({ ...openTask, status: 'COMPLETED' }, now)).toBe(false)
  })

  it('异常上报和管理员闭环应受任务状态限制', () => {
    const claimed = { ...openTask, status: 'CLAIMED' as const, assigneeId: 'OP-1' }
    expect(canOperateTask(claimed, 'OPERATOR', 'OP-1', 'exception')).toBe(true)
    expect(canOperateTask({ ...claimed, status: 'EXCEPTION' }, 'ADMIN', 'ADMIN', 'resolve')).toBe(true)
    expect(canOperateTask({ ...claimed, status: 'PENDING_REVIEW' }, 'ADMIN', 'ADMIN', 'review')).toBe(true)
  })
})

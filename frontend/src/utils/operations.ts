import type {
  OperationsTask,
  OperationsTaskEventType,
  OperationsTaskPriority,
  OperationsTaskStatus,
  OperationsTaskType,
  OperationsExceptionType,
  OperationsTriggerType,
  Organization,
  DataScope,
  UserRole,
} from '@/types/operations'

export const roleLabels: Record<UserRole, string> = {
  ADMIN: '系统管理员',
  OPERATOR: '运营人员',
  AUDITOR: '审计人员',
}

export const dataScopeLabels: Record<DataScope, string> = {
  ALL: '全部数据',
  ORG_AND_CHILDREN: '本组织及下级',
  ORG_ONLY: '仅本组织',
}

export const actionLabels: Record<string, string> = {
  CREATE: '新建',
  UPDATE: '更新',
  DELETE: '停用',
  ACCESS: '访问',
}

export const taskTypeLabels: Record<OperationsTaskType, string> = {
  BATTERY_SWAP: '车辆换电',
  REBALANCE: '车辆调度',
  REPAIR: '故障维修',
  INSPECTION: '车辆巡检',
  RETRIEVAL: '车辆回收',
  CLEANING: '车辆清洁',
}

export const taskStatusLabels: Record<OperationsTaskStatus, string> = {
  OPEN: '待领取',
  CLAIMED: '已领取',
  IN_PROGRESS: '执行中',
  PENDING_REVIEW: '待验收',
  EXCEPTION: '异常待处理',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
}

export const taskPriorityLabels: Record<OperationsTaskPriority, string> = {
  LOW: '低',
  NORMAL: '普通',
  HIGH: '高',
  URGENT: '紧急',
}

export const taskEventLabels: Record<OperationsTaskEventType, string> = {
  CREATED: '创建任务',
  CLAIMED: '抢单',
  ASSIGNED: '指派',
  RELEASED: '释放任务',
  STARTED: '开始执行',
  SUBMITTED: '提交完工',
  COMPLETED: '完成任务',
  CANCELLED: '取消任务',
  DEDUPLICATED: '重复信号合并',
  RULE_RECOVERED: '触发状态恢复',
  EXCEPTION_REPORTED: '上报异常',
  EXCEPTION_RESOLVED: '处理异常',
  REVIEW_APPROVED: '验收通过',
  REVIEW_REJECTED: '验收退回',
}

export const triggerTypeLabels: Record<OperationsTriggerType, string> = {
  LOW_BATTERY: '低电量',
  VEHICLE_FAULT: '车辆故障',
  VEHICLE_OFFLINE: '车辆离线',
  GEO_VIOLATION: '围栏异常',
}

export const exceptionTypeLabels: Record<OperationsExceptionType, string> = {
  VEHICLE_NOT_FOUND: '未找到车辆',
  ACCESS_BLOCKED: '现场无法进入',
  SAFETY_RISK: '存在安全风险',
  PARTS_SHORTAGE: '缺少物料或电池',
  OTHER: '其他异常',
}

/** 输入: 任务; 输出: 是否仍未结束且已经超过要求完成时间。 */
export function isTaskOverdue(task: OperationsTask, now = new Date()): boolean {
  return task.dueAt !== null
    && !['COMPLETED', 'CANCELLED'].includes(task.status)
    && new Date(task.dueAt).getTime() < now.getTime()
}

/** 输入: 任务、角色和当前用户; 输出: 当前用户是否可以执行指定动作。 */
export function canOperateTask(
  task: OperationsTask,
  role: UserRole,
  userId: string,
  action: 'claim' | 'assign' | 'release' | 'start' | 'complete' | 'exception' | 'review' | 'resolve' | 'cancel',
): boolean {
  if (action === 'claim') return role === 'OPERATOR' && task.status === 'OPEN'
  if (action === 'assign') return role === 'ADMIN' && ['OPEN', 'CLAIMED'].includes(task.status)
  if (action === 'cancel') return role === 'ADMIN' && !['COMPLETED', 'CANCELLED'].includes(task.status)
  if (action === 'review') return role === 'ADMIN' && task.status === 'PENDING_REVIEW'
  if (action === 'resolve') return role === 'ADMIN' && task.status === 'EXCEPTION'
  if (role !== 'OPERATOR' || task.assigneeId !== userId) return false
  if (action === 'release' || action === 'start') return task.status === 'CLAIMED'
  if (action === 'exception') return ['CLAIMED', 'IN_PROGRESS'].includes(task.status)
  return action === 'complete' && task.status === 'IN_PROGRESS'
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

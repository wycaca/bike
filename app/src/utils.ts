import type { ExceptionType, TaskPriority, TaskStatus, TaskType } from '@/types'

export const taskTypeLabels: Record<TaskType, string> = {
  BATTERY_SWAP: '换电', REBALANCE: '调度', REPAIR: '维修',
  INSPECTION: '巡检', RETRIEVAL: '回收', CLEANING: '清洁',
}
export const taskStatusLabels: Record<TaskStatus, string> = {
  OPEN: '待领取', CLAIMED: '已领取', IN_PROGRESS: '执行中',
  PENDING_REVIEW: '待验收', EXCEPTION: '异常', COMPLETED: '已完成', CANCELLED: '已取消',
}
export const priorityLabels: Record<TaskPriority, string> = {
  LOW: '低', NORMAL: '普通', HIGH: '高', URGENT: '紧急',
}
export const exceptionLabels: Record<ExceptionType, string> = {
  VEHICLE_NOT_FOUND: '未找到车辆', ACCESS_BLOCKED: '现场无法进入',
  SAFETY_RISK: '存在安全风险', PARTS_SHORTAGE: '缺少物料或电池', OTHER: '其他异常',
}

export function formatTime(value: string | null) {
  if (!value) return '--'
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false,
  }).format(new Date(value))
}

export function formatDistance(value: number) {
  return value >= 1000 ? `${(value / 1000).toFixed(1)} km` : `${value} m`
}

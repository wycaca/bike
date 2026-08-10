import type { RevenueGranularity } from '@/types/operations'

/** 输入: 数值和小数位; 输出: 千分位格式的中文数字。 */
export function formatNumber(value: number, digits = 0): string {
  return new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  }).format(Number.isFinite(value) ? value : 0)
}

/** 输入: 元金额; 输出: 带人民币符号的金额。 */
export function formatMoney(value: number): string {
  return `¥${formatNumber(value, 2)}`
}

/** 输入: 起止日期与粒度; 输出: 报表使用的紧凑周期标签。 */
export function periodLabel(start: string, end: string, granularity: RevenueGranularity): string {
  if (granularity === 'MONTH') return start.slice(0, 7)
  return start === end ? start.slice(5) : `${start.slice(5)}~${end.slice(5)}`
}

/** 输入: 当前值与趋势最大值; 输出: 4% 到 100% 的稳定柱高。 */
export function trendHeight(value: number, maximum: number): number {
  if (maximum <= 0 || value <= 0) return 4
  return Math.min(100, Math.max(4, Math.round((value / maximum) * 100)))
}

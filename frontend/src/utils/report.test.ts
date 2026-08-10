import { describe, expect, it } from 'vitest'

import { formatMoney, isExportTerminal, periodLabel, trendHeight } from '@/utils/report'

describe('收入报表格式化', () => {
  it('金额使用人民币符号和千分位', () => {
    expect(formatMoney(12345.6)).toBe('¥12,345.60')
  })

  it('月报使用年月作为周期标签', () => {
    expect(periodLabel('2026-08-01', '2026-08-10', 'MONTH')).toBe('2026-08')
    expect(periodLabel('2026-08-09', '2026-08-09', 'DAY')).toBe('08-09')
  })

  it('零收入仍保留可见的最小柱高', () => {
    expect(trendHeight(0, 100)).toBe(4)
    expect(trendHeight(50, 100)).toBe(50)
    expect(trendHeight(200, 100)).toBe(100)
  })

  it('只在异步任务结束后停止轮询', () => {
    expect(isExportTerminal('PENDING')).toBe(false)
    expect(isExportTerminal('RUNNING')).toBe(false)
    expect(isExportTerminal('SUCCEEDED')).toBe(true)
    expect(isExportTerminal('FAILED')).toBe(true)
  })
})

import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

import * as api from '@/api'
import type { RevenueValues, TaskSummary } from '@/types'
import AdminOverviewView from '@/views/AdminOverviewView.vue'

vi.mock('@/api', () => ({
  getRevenueReport: vi.fn(), getTaskSummary: vi.fn(), getTasks: vi.fn(),
  errorText: (error: unknown) => String(error),
}))

const revenueValues: RevenueValues = {
  grossBookings: 1288.5, discountAmount: 88.5, refundAmount: 20, netRevenue: 1180,
  completedRides: 236, activeVehicles: 80, vehicleDays: 80, averageDeployedVehicles: 80,
  ridesPerVehicleDay: 2.95, averageRevenuePerRide: 5, revenuePerVehicleDay: 14.75,
  discountRate: 6.87, refundRate: 1.67, averageRideDurationMinutes: 18.2, averageRideDistanceKm: 3.8,
}

const taskSummary: TaskSummary = {
  openCount: 12, claimedCount: 3, inProgressCount: 2, pendingReviewCount: 1,
  exceptionCount: 1, overdueCount: 4, completedTodayCount: 8, myActiveCount: 0,
}

describe('管理员经营总览', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-08-11T09:00:00+08:00'))
    setActivePinia(createPinia())
    vi.mocked(api.getRevenueReport).mockResolvedValue({
      cityCode: '110000', granularity: 'DAY',
      summary: { fromDate: '2026-08-01', toDate: '2026-08-10', values: { ...revenueValues, netRevenue: 10580 } },
      periods: [{ periodStart: '2026-08-10', periodEnd: '2026-08-10', values: revenueValues }],
      generatedAt: '2026-08-11T01:00:00Z',
    })
    vi.mocked(api.getTaskSummary).mockResolvedValue(taskSummary)
    vi.mocked(api.getTasks).mockResolvedValue({ items: [], total: 0, page: 1, pageSize: 100 })
  })

  afterEach(() => vi.useRealTimers())

  it('加载当月至昨日收入并展示单位经济指标', async () => {
    const wrapper = mount(AdminOverviewView)
    await flushPromises()

    expect(api.getRevenueReport).toHaveBeenCalledWith({
      cityCode: '110000', fromDate: '2026-08-01', toDate: '2026-08-10', granularity: 'DAY',
    })
    expect(wrapper.text()).toContain('昨日净收入')
    expect(wrapper.text()).toContain('¥1,180.00')
    expect(wrapper.text()).toContain('¥10,580.00')
    expect(wrapper.text()).toContain('RpD')
    expect(wrapper.text()).toContain('RevPVD')
    expect(wrapper.text()).toContain('2.95')
    expect(wrapper.text()).toContain('¥14.75')
  })

  it('昨日尚未结算时展示最近有订单的结算日', async () => {
    const zeroValues = { ...revenueValues, netRevenue: 0, completedRides: 0, activeVehicles: 0 }
    vi.mocked(api.getRevenueReport).mockResolvedValue({
      cityCode: '110000', granularity: 'DAY',
      summary: { fromDate: '2026-08-01', toDate: '2026-08-10', values: { ...revenueValues, netRevenue: 10580 } },
      periods: [
        { periodStart: '2026-08-09', periodEnd: '2026-08-09', values: revenueValues },
        { periodStart: '2026-08-10', periodEnd: '2026-08-10', values: zeroValues },
      ],
      generatedAt: '2026-08-11T01:00:00Z',
    })
    const wrapper = mount(AdminOverviewView)
    await flushPromises()

    expect(wrapper.text()).toContain('数据截至 2026-08-09')
    expect(wrapper.text()).toContain('最近结算日净收入')
    expect(wrapper.text()).toContain('¥1,180.00')
  })
})

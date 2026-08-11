<script setup lang="ts">
import { showToast } from 'vant'
import { computed, onMounted, ref, watch } from 'vue'

import { errorText, getRevenueReport, getTaskSummary, getTasks } from '@/api'
import TaskCard from '@/components/TaskCard.vue'
import { useAppStore } from '@/stores/app'
import type { RevenueReport, Task, TaskSummary } from '@/types'

const app = useAppStore()
const loading = ref(false)
const revenue = ref<RevenueReport | null>(null)
const summary = ref<TaskSummary>({
  openCount: 0, claimedCount: 0, inProgressCount: 0, pendingReviewCount: 0,
  exceptionCount: 0, overdueCount: 0, completedTodayCount: 0, myActiveCount: 0,
})
const attentionTasks = ref<Task[]>([])

const settledPeriod = computed(() => {
  const periods = revenue.value?.periods ?? []
  return [...periods].reverse().find((period) => period.values.completedRides > 0) ?? periods.at(-1) ?? null
})
const latestDay = computed(() => settledPeriod.value?.values ?? null)
const monthValues = computed(() => revenue.value?.summary.values ?? null)
const recentPeriods = computed(() => revenue.value?.periods.slice(-7) ?? [])
const trendMaximum = computed(() => Math.max(...recentPeriods.value.map((item) => item.values.netRevenue), 1))
const settlementDate = computed(() => settledPeriod.value?.periodStart ?? revenue.value?.summary.toDate ?? '--')
const dailyPrefix = computed(() => settlementDate.value === revenue.value?.summary.toDate ? '昨日' : '最近结算日')

/** 输入: 本机日期; 输出: 截至昨日的当月完整自然日范围。 */
function revenueRange(): { fromDate: string; toDate: string } {
  const yesterday = new Date()
  yesterday.setDate(yesterday.getDate() - 1)
  const monthStart = new Date(yesterday.getFullYear(), yesterday.getMonth(), 1)
  return { fromDate: localDate(monthStart), toDate: localDate(yesterday) }
}

/** 输入: Date; 输出: 不受 UTC 时区转换影响的 YYYY-MM-DD。 */
function localDate(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

/** 输入: 元金额; 输出: 带人民币符号和千分位的金额。 */
function money(value?: number | null): string {
  return `¥${new Intl.NumberFormat('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value ?? 0)}`
}

/** 输入: 经营数值和小数位; 输出: 稳定的中文数字格式。 */
function number(value?: number | null, digits = 0): string {
  return new Intl.NumberFormat('zh-CN', { minimumFractionDigits: digits, maximumFractionDigits: digits }).format(value ?? 0)
}

/** 输入: 每日净收入和近七日峰值; 输出: 12% 至 100% 的趋势柱高度。 */
function trendHeight(value: number): string {
  return `${Math.max(12, Math.round((value / trendMaximum.value) * 100))}%`
}

/** 输入: 当前城市; 输出: 收入经营指标、任务汇总以及需要优先处理的任务。 */
async function loadOverview() {
  loading.value = true
  const range = revenueRange()
  try {
    const [revenueResult, summaryResult, exceptionResult, reviewResult] = await Promise.all([
      getRevenueReport({ cityCode: app.cityCode, ...range, granularity: 'DAY' }),
      getTaskSummary(app.cityCode),
      getTasks({ cityCode: app.cityCode, scope: 'ALL', status: 'EXCEPTION' }),
      getTasks({ cityCode: app.cityCode, scope: 'ALL', status: 'PENDING_REVIEW' }),
    ])
    revenue.value = revenueResult
    summary.value = summaryResult
    attentionTasks.value = [...exceptionResult.items, ...reviewResult.items].slice(0, 6)
  } catch (error) {
    showToast(errorText(error))
  } finally {
    loading.value = false
  }
}

onMounted(loadOverview)
watch(() => app.cityCode, loadOverview)
</script>

<template>
  <div>
    <div class="section-head">
      <h2>经营核心</h2>
      <span>数据截至 {{ settlementDate }}</span>
    </div>

    <section class="revenue-grid" aria-label="收益核心指标">
      <article class="revenue-metric primary">
        <span>{{ dailyPrefix }}净收入</span>
        <strong>{{ money(latestDay?.netRevenue) }}</strong>
        <small>扣除优惠与退款</small>
      </article>
      <article class="revenue-metric month">
        <span>当月累计净收入</span>
        <strong>{{ money(monthValues?.netRevenue) }}</strong>
        <small>{{ monthValues?.completedRides ?? 0 }} 笔有效订单</small>
      </article>
      <article class="revenue-metric"><span>{{ dailyPrefix }}有效订单</span><strong>{{ number(latestDay?.completedRides) }}</strong><small>{{ number(latestDay?.activeVehicles) }} 辆活跃车</small></article>
      <article class="revenue-metric"><span>单车日均骑行</span><strong>{{ number(latestDay?.ridesPerVehicleDay, 2) }}</strong><small>RpD / 车辆周转率</small></article>
      <article class="revenue-metric"><span>单车日均收入</span><strong>{{ money(latestDay?.revenuePerVehicleDay) }}</strong><small>RevPVD</small></article>
      <article class="revenue-metric"><span>单均收入</span><strong>{{ money(latestDay?.averageRevenuePerRide) }}</strong><small>每笔有效订单</small></article>
    </section>

    <section v-if="recentPeriods.length" class="revenue-trend" aria-label="近七日净收入趋势">
      <header><strong>近七日净收入</strong><span>优惠率 {{ number(monthValues?.discountRate, 2) }}% · 退款率 {{ number(monthValues?.refundRate, 2) }}%</span></header>
      <div class="trend-bars">
        <div v-for="period in recentPeriods" :key="period.periodStart" class="trend-day">
          <div><i :style="{ height: trendHeight(period.values.netRevenue) }" /></div>
          <span>{{ period.periodStart.slice(8) }}日</span>
        </div>
      </div>
    </section>

    <div class="section-head content-gap"><h2>今日任务运营</h2><span>{{ app.cityName }}实时任务</span></div>
    <section class="metric-grid">
      <div class="metric"><span>待领取</span><strong>{{ summary.openCount }}</strong></div>
      <div class="metric info"><span>执行中</span><strong>{{ summary.inProgressCount }}</strong></div>
      <div class="metric warning"><span>待验收</span><strong>{{ summary.pendingReviewCount }}</strong></div>
      <div class="metric danger"><span>异常任务</span><strong>{{ summary.exceptionCount }}</strong></div>
      <div class="metric warning"><span>已逾期</span><strong>{{ summary.overdueCount }}</strong></div>
      <div class="metric"><span>今日完成</span><strong>{{ summary.completedTodayCount }}</strong></div>
    </section>

    <div class="section-head content-gap"><h2>待处理事项</h2><span>{{ attentionTasks.length }} 项</span></div>
    <div v-if="attentionTasks.length" class="task-list">
      <TaskCard v-for="task in attentionTasks" :key="task.taskId" :task="task" />
    </div>
    <van-loading v-else-if="loading" class="center-loading" />
    <div v-else class="empty-state">当前没有待验收或异常任务</div>
  </div>
</template>

<style scoped>
.revenue-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.revenue-metric { display: flex; min-height: 92px; flex-direction: column; justify-content: space-between; padding: 12px; border-left: 3px solid #2774a8; background: #fff; }
.revenue-metric.primary, .revenue-metric.month { min-height: 108px; color: #fff; border: 0; }
.revenue-metric.primary { background: var(--brand-dark); }
.revenue-metric.month { background: #245f78; }
.revenue-metric span { color: var(--muted); font-size: 11px; }
.revenue-metric strong { font-size: 21px; font-variant-numeric: tabular-nums; }
.revenue-metric small { color: #85918c; font-size: 10px; }
.revenue-metric.primary span, .revenue-metric.primary small, .revenue-metric.month span, .revenue-metric.month small { color: #d2e3dd; }
.revenue-metric.primary strong, .revenue-metric.month strong { font-size: 24px; }
.revenue-trend { margin-top: 10px; padding: 12px; background: #fff; }
.revenue-trend header { display: flex; align-items: baseline; justify-content: space-between; gap: 8px; }
.revenue-trend header strong { font-size: 13px; }.revenue-trend header span { color: var(--muted); font-size: 9px; }
.trend-bars { display: grid; grid-template-columns: repeat(7, 1fr); height: 88px; gap: 6px; margin-top: 10px; }
.trend-day { display: grid; grid-template-rows: 1fr 16px; gap: 3px; text-align: center; }
.trend-day > div { display: flex; align-items: flex-end; justify-content: center; border-bottom: 1px solid var(--line); }
.trend-day i { display: block; width: min(20px, 70%); background: #2b7a57; }
.trend-day span { color: var(--muted); font-size: 9px; }
</style>

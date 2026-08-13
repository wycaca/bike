<script setup lang="ts">
import { Download, InfoFilled, Loading, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

import { createRevenueExport, downloadReportExport, getReportExport, getRevenueReport } from '@/api/report'
import { errorMessage } from '@/api/http'
import { useAppStore } from '@/stores/app'
import type { ReportExportJob, RevenueGranularity, RevenueReport, RevenueValues } from '@/types/operations'
import { formatMoney, formatNumber, isExportTerminal, periodLabel, trendHeight } from '@/utils/report'
import { formatTime } from '@/utils/vehicle'

const appStore = useAppStore()
const granularity = ref<RevenueGranularity>('DAY')
const dateRange = ref<[string, string]>(defaultDateRange())
const data = ref<RevenueReport | null>(null)
const loading = ref(false)
const exporting = ref(false)
const exportJob = ref<ReportExportJob | null>(null)
const error = ref('')
const summary = computed<RevenueValues | null>(() => data.value?.summary.values ?? null)
const maxGross = computed(() => Math.max(...(data.value?.periods.map((item) => item.values.grossBookings) ?? [1]), 1))
const chartWidth = computed(() => `${Math.max((data.value?.periods.length ?? 0) * 48, 720)}px`)
const exportStatusText = computed(() => {
  if (!exportJob.value) return ''
  return {
    PENDING: '等待生成', RUNNING: '正在生成', SUCCEEDED: '生成完成',
    FAILED: '生成失败', EXPIRED: '文件已过期',
  }[exportJob.value.status]
})
let exportPollTimer: number | undefined

/** 输入: 本机日期; 输出: 截至昨日的最近 30 个完整自然日。 */
function defaultDateRange(): [string, string] {
  const end = new Date()
  end.setDate(end.getDate() - 1)
  const start = new Date(end)
  start.setDate(start.getDate() - 29)
  return [localDate(start), localDate(end)]
}

/** 输入: Date; 输出: 不受 UTC 偏移影响的 YYYY-MM-DD。 */
function localDate(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

/** 输入: 当前筛选条件; 输出: 最新收入报表。 */
async function loadReport() {
  if (!dateRange.value?.[0] || !dateRange.value?.[1]) return
  loading.value = true
  error.value = ''
  try {
    data.value = await getRevenueReport({
      cityCode: appStore.cityCode,
      fromDate: dateRange.value[0],
      toDate: dateRange.value[1],
      granularity: granularity.value,
    })
  } catch (cause) {
    error.value = errorMessage(cause)
  } finally {
    loading.value = false
  }
}

/** 输入: 当前报表筛选; 输出: 创建由独立 Worker 处理的异步任务。 */
async function exportReport() {
  if (!dateRange.value?.[0] || !dateRange.value?.[1]) return
  exporting.value = true
  try {
    exportJob.value = await createRevenueExport({
      cityCode: appStore.cityCode,
      fromDate: dateRange.value[0],
      toDate: dateRange.value[1],
      granularity: granularity.value,
    })
    ElMessage.info('报表任务已提交，后台正在生成')
    scheduleExportPoll()
  } catch (cause) {
    error.value = errorMessage(cause)
    exporting.value = false
  }
}

/** 输入: 当前任务; 输出: 定时查询 Worker 状态，成功后触发文件下载。 */
function scheduleExportPoll() {
  window.clearTimeout(exportPollTimer)
  exportPollTimer = window.setTimeout(pollExport, 1_000)
}

async function pollExport() {
  if (!exportJob.value) return
  try {
    exportJob.value = await getReportExport(exportJob.value.jobId)
    if (!isExportTerminal(exportJob.value.status)) {
      scheduleExportPoll()
      return
    }
    exporting.value = false
    if (exportJob.value.status === 'SUCCEEDED') {
      const blob = await downloadReportExport(exportJob.value.jobId)
      saveBlob(blob, exportJob.value.outputFileName)
      ElMessage.success('收入报表已生成并下载')
    } else {
      error.value = exportJob.value.errorMessage || '报表生成失败，请重新提交'
    }
  } catch (cause) {
    exporting.value = false
    error.value = errorMessage(cause)
  }
}

/** 输入: 文件二进制和服务端文件名; 输出: 浏览器下载。 */
function saveBlob(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.style.display = 'none'
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 1_000)
}

watch([() => appStore.cityCode, granularity], loadReport)
onMounted(loadReport)
onBeforeUnmount(() => window.clearTimeout(exportPollTimer))
</script>

<template>
  <div v-loading="loading" class="page-view revenue-page">
    <div class="revenue-heading">
      <div class="page-heading">
        <div>
          <h1>收入与经营报表</h1>
          <p>{{ appStore.cityName }}骑行收入、车辆周转与单位经济指标</p>
        </div>
        <el-button :icon="Refresh" circle aria-label="刷新" @click="loadReport" />
      </div>
    </div>

    <div class="report-toolbar">
      <el-radio-group v-model="appStore.cityCode">
        <el-radio-button v-for="city in appStore.cities" :key="city.code" :value="city.code">
          {{ city.name }}
        </el-radio-button>
      </el-radio-group>
      <el-date-picker
        v-model="dateRange"
        type="daterange"
        value-format="YYYY-MM-DD"
        range-separator="至"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        :clearable="false"
        style="width: 260px"
      />
      <el-radio-group v-model="granularity">
        <el-radio-button value="DAY">日报</el-radio-button>
        <el-radio-button value="MONTH">月报</el-radio-button>
      </el-radio-group>
      <el-button type="primary" :icon="Search" @click="loadReport">查询</el-button>
      <span class="toolbar-spacer" />
      <span v-if="exportJob" class="export-status">
        <el-icon v-if="!isExportTerminal(exportJob.status)" class="is-loading"><Loading /></el-icon>
        {{ exportStatusText }}
      </span>
      <el-button :icon="Download" :loading="exporting" @click="exportReport">导出 CSV</el-button>
    </div>

    <div class="report-body">
      <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />

      <template v-if="data && summary">
        <section class="revenue-metrics" aria-label="收入核心指标">
          <article class="primary">
            <span>净收入</span><strong>{{ formatMoney(summary.netRevenue) }}</strong>
            <small>扣除优惠与退款</small>
          </article>
          <article>
            <span>总流水</span><strong>{{ formatMoney(summary.grossBookings) }}</strong>
            <small>骑行订单应收金额</small>
          </article>
          <article>
            <span>有效订单</span><strong>{{ formatNumber(summary.completedRides) }}</strong>
            <small>{{ summary.activeVehicles }} 辆车产生骑行</small>
          </article>
          <article>
            <span class="metric-title">单车日均骑行次数
              <el-tooltip content="RpD：有效订单数 / 投放车辆日数，用于衡量车辆周转效率">
                <el-icon><InfoFilled /></el-icon>
              </el-tooltip>
            </span>
            <strong>{{ formatNumber(summary.ridesPerVehicleDay, 2) }}</strong><small>RpD / 周转率</small>
          </article>
          <article>
            <span class="metric-title">单车日均收入
              <el-tooltip content="RevPVD：净收入 / 投放车辆日数，综合反映周转和单均收入">
                <el-icon><InfoFilled /></el-icon>
              </el-tooltip>
            </span>
            <strong>{{ formatMoney(summary.revenuePerVehicleDay) }}</strong><small>Revenue per Vehicle per Day</small>
          </article>
          <article>
            <span class="metric-title">单均收入
              <el-tooltip content="ARPR：净收入 / 有效订单数">
                <el-icon><InfoFilled /></el-icon>
              </el-tooltip>
            </span>
            <strong>{{ formatMoney(summary.averageRevenuePerRide) }}</strong><small>Average Revenue per Ride</small>
          </article>
        </section>

        <section class="report-context" aria-label="辅助经营指标">
          <div><span>平均投放车辆</span><strong>{{ formatNumber(summary.averageDeployedVehicles, 1) }} 辆</strong></div>
          <div><span>优惠金额 / 优惠率</span><strong>{{ formatMoney(summary.discountAmount) }} / {{ formatNumber(summary.discountRate, 2) }}%</strong></div>
          <div><span>退款金额 / 退款率</span><strong>{{ formatMoney(summary.refundAmount) }} / {{ formatNumber(summary.refundRate, 2) }}%</strong></div>
          <div><span>平均骑行时长</span><strong>{{ formatNumber(summary.averageRideDurationMinutes, 1) }} 分钟</strong></div>
          <div><span>平均骑行距离</span><strong>{{ formatNumber(summary.averageRideDistanceKm, 2) }} 公里</strong></div>
        </section>

        <section class="revenue-section trend-section">
          <header>
            <div><h2>收入趋势</h2><p>总流水与扣除优惠、退款后的净收入</p></div>
            <div class="trend-legend"><span><i class="gross" />总流水</span><span><i class="net" />净收入</span></div>
          </header>
          <div class="revenue-chart-scroll">
            <div v-if="data.periods.length" class="revenue-chart" :style="{ minWidth: chartWidth }">
              <article v-for="item in data.periods" :key="item.periodStart" class="revenue-column">
                <span class="column-value">{{ formatNumber(item.values.netRevenue, 0) }}</span>
                <div class="revenue-track" :title="`${periodLabel(item.periodStart, item.periodEnd, data.granularity)} 净收入 ${formatMoney(item.values.netRevenue)}`">
                  <i class="gross-bar" :style="{ height: `${trendHeight(item.values.grossBookings, maxGross)}%` }" />
                  <i class="net-bar" :style="{ height: `${trendHeight(item.values.netRevenue, maxGross)}%` }" />
                </div>
                <strong>{{ periodLabel(item.periodStart, item.periodEnd, data.granularity) }}</strong>
                <small>{{ item.values.completedRides }} 单</small>
              </article>
            </div>
            <el-empty v-else description="当前周期暂无收入数据" :image-size="64" />
          </div>
        </section>

        <section class="revenue-section detail-section">
          <header>
            <div><h2>周期明细</h2><p>{{ data.summary.fromDate }} 至 {{ data.summary.toDate }}</p></div>
            <small>生成时间 {{ formatTime(data.generatedAt) }}</small>
          </header>
          <el-table :data="data.periods" stripe>
            <el-table-column label="周期" min-width="108" fixed>
              <template #default="scope">{{ periodLabel(scope.row.periodStart, scope.row.periodEnd, data.granularity) }}</template>
            </el-table-column>
            <el-table-column label="净收入" min-width="120" align="right"><template #default="scope">{{ formatMoney(scope.row.values.netRevenue) }}</template></el-table-column>
            <el-table-column label="总流水" min-width="120" align="right"><template #default="scope">{{ formatMoney(scope.row.values.grossBookings) }}</template></el-table-column>
            <el-table-column label="有效订单" min-width="96" align="right"><template #default="scope">{{ formatNumber(scope.row.values.completedRides) }}</template></el-table-column>
            <el-table-column label="活跃车辆" min-width="96" align="right"><template #default="scope">{{ scope.row.values.activeVehicles }}</template></el-table-column>
            <el-table-column label="RpD" min-width="82" align="right"><template #default="scope">{{ formatNumber(scope.row.values.ridesPerVehicleDay, 2) }}</template></el-table-column>
            <el-table-column label="单均收入" min-width="112" align="right"><template #default="scope">{{ formatMoney(scope.row.values.averageRevenuePerRide) }}</template></el-table-column>
            <el-table-column label="单车日均收入" min-width="132" align="right"><template #default="scope">{{ formatMoney(scope.row.values.revenuePerVehicleDay) }}</template></el-table-column>
            <el-table-column label="优惠率" min-width="88" align="right"><template #default="scope">{{ formatNumber(scope.row.values.discountRate, 2) }}%</template></el-table-column>
            <el-table-column label="退款率" min-width="88" align="right"><template #default="scope">{{ formatNumber(scope.row.values.refundRate, 2) }}%</template></el-table-column>
            <el-table-column label="平均时长" min-width="96" align="right"><template #default="scope">{{ formatNumber(scope.row.values.averageRideDurationMinutes, 1) }} 分</template></el-table-column>
            <el-table-column label="平均距离" min-width="96" align="right"><template #default="scope">{{ formatNumber(scope.row.values.averageRideDistanceKm, 2) }} km</template></el-table-column>
          </el-table>
        </section>
      </template>
    </div>
  </div>
</template>

<style scoped>
.revenue-page { display: grid; grid-template-rows: 78px 58px minmax(0, 1fr); background: #eef1ef; }
.revenue-heading { display: flex; align-items: center; padding: 0 18px; background: #fff; border-bottom: 1px solid var(--line); }
.revenue-heading .page-heading { width: 100%; }
.report-toolbar { display: flex; align-items: center; gap: 9px; padding: 8px 16px; background: #fff; border-bottom: 1px solid var(--line); }
.toolbar-spacer { flex: 1; }
.export-status { display: flex; align-items: center; gap: 5px; color: var(--muted); font-size: 12px; white-space: nowrap; }
.report-body { min-height: 0; padding: 14px 16px 20px; overflow: auto; }
.report-body > .el-alert { margin-bottom: 12px; }
.revenue-metrics { display: grid; grid-template-columns: repeat(6, minmax(142px, 1fr)); gap: 10px; }
.revenue-metrics article { min-height: 112px; padding: 14px 15px; background: #fff; border: 1px solid #dce2df; border-top: 3px solid #4f6860; border-radius: 6px; }
.revenue-metrics article.primary { border-top-color: #16805a; }
.revenue-metrics span, .revenue-metrics small { display: block; color: var(--muted); font-size: 12px; }
.revenue-metrics strong { display: block; margin: 8px 0 5px; font-size: 25px; font-variant-numeric: tabular-nums; white-space: nowrap; }
.metric-title { display: flex !important; align-items: center; gap: 4px; }
.metric-title .el-icon { color: #7c8984; font-size: 13px; }
.report-context { display: grid; grid-template-columns: repeat(5, 1fr); margin: 10px 0; background: #fff; border: 1px solid #dce2df; }
.report-context div { min-height: 58px; padding: 10px 14px; border-right: 1px solid #e3e8e5; }
.report-context div:last-child { border-right: 0; }
.report-context span, .report-context strong { display: block; }
.report-context span { color: var(--muted); font-size: 11px; }
.report-context strong { margin-top: 6px; font-size: 13px; font-variant-numeric: tabular-nums; }
.revenue-section { margin-top: 10px; background: #fff; border: 1px solid #dce2df; }
.revenue-section > header { display: flex; align-items: center; justify-content: space-between; min-height: 62px; padding: 11px 16px; border-bottom: 1px solid #e4e8e6; }
.revenue-section h2 { margin: 0; font-size: 15px; }
.revenue-section header p, .revenue-section header small { margin: 4px 0 0; color: var(--muted); font-size: 11px; }
.trend-legend { display: flex; gap: 14px; color: var(--muted); font-size: 11px; }
.trend-legend span { display: flex; align-items: center; gap: 5px; }
.trend-legend i { width: 9px; height: 9px; }.trend-legend i.gross { background: #cad3cf; }.trend-legend i.net { background: #23805f; }
.revenue-chart-scroll { overflow-x: auto; }
.revenue-chart { display: flex; align-items: flex-end; gap: 7px; height: 260px; padding: 24px 16px 12px; }
.revenue-column { display: grid; grid-template-rows: 18px minmax(0, 1fr) 21px 17px; flex: 1; align-items: end; min-width: 34px; height: 100%; text-align: center; }
.column-value, .revenue-column small { color: #73807a; font-size: 9px; }
.revenue-track { position: relative; align-self: stretch; width: min(30px, 82%); margin: 0 auto; background: #f2f4f3; }
.revenue-track i { position: absolute; right: 0; bottom: 0; left: 0; }
.gross-bar { background: #cad3cf; }.net-bar { right: 5px !important; left: 5px !important; background: #23805f; }
.revenue-column strong { padding-top: 5px; font-size: 10px; font-weight: 600; white-space: nowrap; }
.detail-section :deep(.el-table__header th) { background: #f2f5f3; }
@media (max-width: 1380px) { .revenue-metrics { grid-template-columns: repeat(3, 1fr); }.report-context { grid-template-columns: repeat(3, 1fr); }.report-context div:nth-child(3) { border-right: 0; } }
</style>

<script setup lang="ts">
import { Download, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

import { getDashboard } from '@/api/dashboard'
import { errorMessage } from '@/api/http'
import { createVehicleStatusExport, downloadReportExport, getReportExport } from '@/api/report'
import { useAppStore } from '@/stores/app'
import type { DashboardData } from '@/types/operations'
import { CITIES, cityName, formatTime } from '@/utils/vehicle'

const appStore = useAppStore()
const days = ref(7)
const data = ref<DashboardData | null>(null)
const loading = ref(false)
const exporting = ref(false)
const error = ref('')
let exportCancelled = false
const maxReports = computed(() => Math.max(...(data.value?.trends.map((item) => item.telemetryReports) ?? [1]), 1))

/** 输入: 当前城市和周期; 输出: 最新看板数据。 */
async function loadDashboard() {
  loading.value = true
  error.value = ''
  try {
    data.value = await getDashboard(appStore.cityCode, days.value)
  } catch (cause) {
    error.value = errorMessage(cause)
  } finally {
    loading.value = false
  }
}

/** 输入: 当前城市; 输出: 由独立 Worker 异步生成并下载车辆状态 CSV。 */
async function exportReport() {
  exporting.value = true
  exportCancelled = false
  try {
    let job = await createVehicleStatusExport(appStore.cityCode)
    while (!exportCancelled && (job.status === 'PENDING' || job.status === 'RUNNING')) {
      await new Promise((resolve) => window.setTimeout(resolve, 1_000))
      job = await getReportExport(job.jobId)
    }
    if (exportCancelled) return
    if (job.status !== 'SUCCEEDED') {
      throw new Error(job.errorMessage || '报表生成失败')
    }
    const blob = await downloadReportExport(job.jobId)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `车辆状态-${appStore.cityCode}.csv`
    link.style.display = 'none'
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.setTimeout(() => URL.revokeObjectURL(url), 1_000)
    ElMessage.success('报表已生成')
  } catch (cause) {
    error.value = errorMessage(cause)
  } finally {
    exporting.value = false
  }
}

watch([() => appStore.cityCode, days], loadDashboard)
onMounted(loadDashboard)
onBeforeUnmount(() => { exportCancelled = true })
</script>

<template>
  <div v-loading="loading" class="page-view dashboard-page">
    <div class="dashboard-heading">
      <div class="page-heading">
        <div>
          <h1>运营看板</h1>
          <p>{{ cityName(appStore.cityCode) }}车辆状态、遥测活跃度与区域分布</p>
        </div>
        <div class="dashboard-actions">
          <el-radio-group v-model="appStore.cityCode">
            <el-radio-button v-for="city in CITIES" :key="city.code" :value="city.code">
              {{ city.name }}
            </el-radio-button>
          </el-radio-group>
          <el-select v-model="days" aria-label="趋势周期" style="width: 104px">
            <el-option label="近 7 天" :value="7" />
            <el-option label="近 14 天" :value="14" />
            <el-option label="近 30 天" :value="30" />
          </el-select>
          <el-tooltip content="导出车辆状态 CSV">
            <el-button :icon="Download" :loading="exporting" @click="exportReport">导出报表</el-button>
          </el-tooltip>
          <el-button :icon="Refresh" circle aria-label="刷新" @click="loadDashboard" />
        </div>
      </div>
    </div>

    <el-alert v-if="error" class="dashboard-error" :title="error" type="error" show-icon />

    <div v-if="data" class="dashboard-scroll">
      <section class="metric-grid" aria-label="车辆核心指标">
        <article><span>车辆总数</span><strong>{{ data.summary.totalVehicles }}</strong><small>台在册车辆</small></article>
        <article><span>在线车辆</span><strong>{{ data.summary.onlineVehicles }}</strong><small>{{ data.summary.onlineRate }}% 在线率</small></article>
        <article><span>骑行中</span><strong>{{ data.summary.ridingVehicles }}</strong><small>实时骑行状态</small></article>
        <article class="warning"><span>低电量</span><strong>{{ data.summary.lowBatteryVehicles }}</strong><small>电量低于 20%</small></article>
        <article class="danger"><span>故障车辆</span><strong>{{ data.summary.faultVehicles }}</strong><small>需运营人员关注</small></article>
        <article><span>维护中</span><strong>{{ data.summary.maintenanceVehicles }}</strong><small>{{ data.summary.offlineVehicles }} 台离线</small></article>
      </section>

      <div class="dashboard-columns">
        <section class="dashboard-section trend-section">
          <header><div><h2>遥测活跃趋势</h2><p>按日统计上报量与活跃车辆</p></div></header>
          <div v-if="data.trends.length" class="trend-chart">
            <div v-for="item in data.trends" :key="item.date" class="trend-column">
              <span class="trend-value">{{ item.telemetryReports }}</span>
              <div class="trend-track">
                <i :style="{ height: `${Math.max((item.telemetryReports / maxReports) * 100, 4)}%` }" />
              </div>
              <strong>{{ item.date.slice(5) }}</strong>
              <small>{{ item.activeVehicles }} 辆</small>
            </div>
          </div>
          <el-empty v-else description="当前周期暂无遥测数据" :image-size="64" />
        </section>

        <section class="dashboard-section exception-section">
          <header><div><h2>运营关注</h2><p>当前需要处理的车辆状态</p></div></header>
          <dl>
            <div><dt><i class="status-mark offline" />离线车辆</dt><dd>{{ data.summary.offlineVehicles }}</dd></div>
            <div><dt><i class="status-mark low" />低电量车辆</dt><dd>{{ data.summary.lowBatteryVehicles }}</dd></div>
            <div><dt><i class="status-mark fault" />控制器或故障码异常</dt><dd>{{ data.summary.faultVehicles }}</dd></div>
            <div><dt><i class="status-mark maintenance" />维护中车辆</dt><dd>{{ data.summary.maintenanceVehicles }}</dd></div>
          </dl>
        </section>
      </div>

      <section class="dashboard-section area-section">
        <header>
          <div><h2>运营区域分布</h2><p>按区域比较车辆规模和异常状态</p></div>
          <small>生成时间 {{ formatTime(data.generatedAt) }}</small>
        </header>
        <el-table :data="data.areas" stripe>
          <el-table-column prop="areaCode" label="运营区代码" min-width="160" />
          <el-table-column prop="vehicleCount" label="车辆数" align="right" />
          <el-table-column prop="onlineCount" label="在线" align="right" />
          <el-table-column prop="lowBatteryCount" label="低电量" align="right" />
          <el-table-column prop="faultCount" label="故障" align="right" />
          <el-table-column label="在线率" min-width="180">
            <template #default="scope">
              <el-progress
                :percentage="Math.round((scope.row.onlineCount / scope.row.vehicleCount) * 100)"
                :stroke-width="8"
                color="#26835f"
              />
            </template>
          </el-table-column>
        </el-table>
      </section>
    </div>
  </div>
</template>

<style scoped>
.dashboard-page { display: grid; grid-template-rows: 78px auto minmax(0, 1fr); background: #eef1ef; }
.dashboard-heading { display: flex; align-items: center; padding: 0 18px; background: #fff; border-bottom: 1px solid var(--line); }
.dashboard-heading .page-heading { width: 100%; }
.dashboard-actions { display: flex; align-items: center; gap: 9px; }
.dashboard-error { margin: 10px 16px 0; width: auto; }
.dashboard-scroll { min-height: 0; padding: 16px; overflow: auto; }
.metric-grid { display: grid; grid-template-columns: repeat(6, minmax(130px, 1fr)); gap: 10px; }
.metric-grid article { min-height: 112px; padding: 15px 16px; background: #fff; border: 1px solid #dce2df; border-top: 3px solid #2c8262; border-radius: 6px; }
.metric-grid article.warning { border-top-color: #c27a12; }
.metric-grid article.danger { border-top-color: #c94747; }
.metric-grid span, .metric-grid small { display: block; color: var(--muted); font-size: 12px; }
.metric-grid strong { display: block; margin: 7px 0 5px; font-size: 28px; font-variant-numeric: tabular-nums; }
.dashboard-columns { display: grid; grid-template-columns: minmax(0, 2fr) minmax(270px, 1fr); gap: 12px; margin-top: 12px; }
.dashboard-section { background: #fff; border: 1px solid #dce2df; }
.dashboard-section > header { display: flex; align-items: center; justify-content: space-between; min-height: 62px; padding: 11px 16px; border-bottom: 1px solid #e4e8e6; }
.dashboard-section h2 { margin: 0; font-size: 15px; }
.dashboard-section header p, .dashboard-section header small { margin: 4px 0 0; color: var(--muted); font-size: 11px; }
.trend-chart { display: flex; align-items: flex-end; gap: 10px; height: 250px; padding: 28px 18px 12px; }
.trend-column { display: grid; grid-template-rows: 18px minmax(0, 1fr) 20px 18px; flex: 1; align-items: end; height: 100%; text-align: center; }
.trend-value, .trend-column small { color: #73807a; font-size: 10px; }
.trend-track { position: relative; align-self: stretch; margin: 0 auto; width: min(34px, 72%); background: #edf2ef; }
.trend-track i { position: absolute; right: 0; bottom: 0; left: 0; background: #2b8764; }
.trend-column strong { padding-top: 5px; font-size: 11px; font-weight: 600; }
.exception-section dl { margin: 0; padding: 4px 16px; }
.exception-section dl div { display: flex; align-items: center; justify-content: space-between; min-height: 45px; border-bottom: 1px solid #edf0ee; }
.exception-section dl div:last-child { border-bottom: 0; }
.exception-section dt { display: flex; align-items: center; color: #4b5853; font-size: 12px; }
.exception-section dd { margin: 0; font-weight: 700; font-variant-numeric: tabular-nums; }
.status-mark { width: 8px; height: 8px; margin-right: 8px; border-radius: 50%; }
.status-mark.offline { background: #6e7773; }.status-mark.low { background: #cf841a; }.status-mark.fault { background: #ca4747; }.status-mark.maintenance { background: #3c75ad; }
.area-section { margin-top: 12px; }
.area-section :deep(.el-table__header th) { background: #f2f5f3; }
@media (max-width: 1320px) { .metric-grid { grid-template-columns: repeat(3, 1fr); } }
</style>

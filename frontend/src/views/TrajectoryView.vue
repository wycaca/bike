<script setup lang="ts">
import { ArrowLeft, Position, VideoPause, VideoPlay } from '@element-plus/icons-vue'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { errorMessage } from '@/api/http'
import { getTrajectory, getVehicle } from '@/api/vehicle'
import TrajectoryCanvas from '@/components/TrajectoryCanvas.vue'
import VehicleConditionTag from '@/components/VehicleConditionTag.vue'
import type { TrajectoryPoint, VehicleDetail } from '@/types/vehicle'
import { formatTime, lockLabels, rideLabels, trajectoryDistanceKm } from '@/utils/vehicle'

const route = useRoute()
const router = useRouter()
const vehicleId = computed(() => String(route.params.vehicleId))
const detail = ref<VehicleDetail | null>(null)
const points = ref<TrajectoryPoint[]>([])
const timeRange = ref<[Date, Date] | null>(null)
const loading = ref(false)
const error = ref('')
const truncated = ref(false)
const activeIndex = ref(0)
const playing = ref(false)
const playbackSpeed = ref(1)
let requestController: AbortController | null = null
let playbackTimer: number | null = null

const activePoint = computed(() => points.value[activeIndex.value] ?? null)
const distanceKm = computed(() => trajectoryDistanceKm(points.value))
const durationMinutes = computed(() => {
  if (points.value.length < 2) return 0
  const first = points.value[0]!
  const last = points.value[points.value.length - 1]!
  return Math.max(0, Math.round((Date.parse(last.reportedAt) - Date.parse(first.reportedAt)) / 60_000))
})

function defaultRange(reportedAt?: string) {
  const end = reportedAt ? new Date(Date.parse(reportedAt) + 60_000) : new Date()
  return [new Date(end.getTime() - 2 * 60 * 60 * 1000), end] as [Date, Date]
}

async function loadPage() {
  requestController?.abort()
  requestController = new AbortController()
  loading.value = true
  error.value = ''
  stopPlayback()
  try {
    detail.value = await getVehicle(vehicleId.value, requestController.signal)
    if (!timeRange.value) {
      timeRange.value = defaultRange(detail.value.latestState?.reportedAt)
    }
    await loadTrajectory(requestController.signal)
  } catch (cause) {
    const message = errorMessage(cause)
    if (message) error.value = message
  } finally {
    loading.value = false
  }
}

async function loadTrajectory(signal?: AbortSignal) {
  if (!timeRange.value) return
  loading.value = true
  error.value = ''
  stopPlayback()
  try {
    const result = await getTrajectory(
      vehicleId.value,
      timeRange.value[0].toISOString(),
      timeRange.value[1].toISOString(),
      signal,
    )
    points.value = result.points
    truncated.value = result.truncated
    activeIndex.value = 0
  } catch (cause) {
    const message = errorMessage(cause)
    if (message) error.value = message
  } finally {
    loading.value = false
  }
}

function stopPlayback() {
  playing.value = false
  if (playbackTimer) window.clearInterval(playbackTimer)
  playbackTimer = null
}

function togglePlayback() {
  if (playing.value) {
    stopPlayback()
    return
  }
  if (points.value.length < 2) return
  if (activeIndex.value >= points.value.length - 1) activeIndex.value = 0
  playing.value = true
  playbackTimer = window.setInterval(() => {
    if (activeIndex.value >= points.value.length - 1) {
      stopPlayback()
      return
    }
    activeIndex.value += 1
  }, 1000 / playbackSpeed.value)
}

watch(playbackSpeed, () => {
  if (playing.value) {
    stopPlayback()
    togglePlayback()
  }
})
watch(vehicleId, () => {
  timeRange.value = null
  void loadPage()
}, { immediate: true })
onBeforeUnmount(() => {
  requestController?.abort()
  stopPlayback()
})
</script>

<template>
  <div class="page-view trajectory-page">
    <div class="trajectory-heading">
      <el-button :icon="ArrowLeft" text @click="router.back()">返回</el-button>
      <div class="page-heading">
        <div>
          <h1>{{ vehicleId }} 历史轨迹</h1>
          <p>{{ detail?.asset.plateNumber || detail?.asset.model || '车辆轨迹回放' }}</p>
        </div>
        <VehicleConditionTag v-if="detail" :state="detail.latestState" />
      </div>
    </div>

    <div class="toolbar-band trajectory-toolbar">
      <el-date-picker
        v-model="timeRange"
        type="datetimerange"
        range-separator="至"
        start-placeholder="开始时间"
        end-placeholder="结束时间"
        :clearable="false"
      />
      <el-button type="primary" :icon="Position" :loading="loading" @click="loadTrajectory()">
        查询轨迹
      </el-button>
      <span class="query-limit">单次最多 31 天 / 10000 点</span>
    </div>

    <el-alert
      v-if="error"
      class="trajectory-alert"
      :title="error"
      type="error"
      show-icon
      :closable="false"
    />
    <el-alert
      v-if="truncated"
      class="trajectory-alert"
      title="轨迹点已达到返回上限, 请缩小时间范围"
      type="warning"
      show-icon
      :closable="false"
    />

    <div class="trajectory-workspace" v-loading="loading">
      <div class="trajectory-visual">
        <TrajectoryCanvas v-if="points.length" :points="points" :active-index="activeIndex" />
        <el-empty v-else description="当前时间范围没有轨迹" />

        <div v-if="points.length" class="playback-bar">
          <el-button
            :icon="playing ? VideoPause : VideoPlay"
            circle
            :aria-label="playing ? '暂停' : '播放'"
            @click="togglePlayback"
          />
          <el-slider
            v-model="activeIndex"
            :min="0"
            :max="Math.max(0, points.length - 1)"
            :show-tooltip="false"
            @input="stopPlayback"
          />
          <span class="playback-count">{{ activeIndex + 1 }} / {{ points.length }}</span>
          <el-select v-model="playbackSpeed" aria-label="播放速度" style="width: 78px">
            <el-option label="0.5x" :value="0.5" />
            <el-option label="1x" :value="1" />
            <el-option label="2x" :value="2" />
          </el-select>
        </div>
      </div>

      <aside class="trajectory-panel">
        <section>
          <h2 class="section-title">轨迹摘要</h2>
          <div class="summary-list">
            <div><span>轨迹点</span><strong>{{ points.length }}</strong></div>
            <div><span>估算距离</span><strong>{{ distanceKm.toFixed(2) }} km</strong></div>
            <div><span>时间跨度</span><strong>{{ durationMinutes }} min</strong></div>
            <div><span>坐标系</span><strong>{{ points[0]?.coordinateSystem ?? '--' }}</strong></div>
          </div>
        </section>

        <section v-if="activePoint" class="active-point-section">
          <h2 class="section-title">当前轨迹点</h2>
          <dl class="point-details">
            <div><dt>时间</dt><dd>{{ formatTime(activePoint.reportedAt) }}</dd></div>
            <div><dt>经度</dt><dd>{{ activePoint.longitude.toFixed(6) }}</dd></div>
            <div><dt>纬度</dt><dd>{{ activePoint.latitude.toFixed(6) }}</dd></div>
            <div><dt>速度</dt><dd>{{ activePoint.speedKmh ?? '--' }} km/h</dd></div>
            <div><dt>电量</dt><dd>{{ activePoint.batteryPercent ?? '--' }}%</dd></div>
            <div><dt>车辆锁</dt><dd>{{ lockLabels[activePoint.lockStatus] }}</dd></div>
            <div><dt>骑行状态</dt><dd>{{ rideLabels[activePoint.rideStatus] }}</dd></div>
          </dl>
        </section>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.trajectory-page {
  display: grid;
  grid-template-rows: 78px 58px auto minmax(0, 1fr);
  background: #ffffff;
}

.trajectory-heading {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: 8px;
  padding: 0 18px 0 8px;
  border-bottom: 1px solid var(--line);
}

.trajectory-heading .page-heading {
  width: 100%;
}

.trajectory-toolbar {
  min-height: 58px;
}

.query-limit {
  color: #737f7a;
  font-size: 12px;
}

.trajectory-alert {
  margin: 10px 14px 0;
  width: auto;
}

.trajectory-workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 290px;
  min-height: 0;
  padding: 14px;
  gap: 14px;
  background: #eef1ef;
}

.trajectory-visual {
  display: grid;
  grid-template-rows: minmax(0, 1fr) 62px;
  min-height: 0;
  background: #ffffff;
  border: 1px solid #d7dedb;
}

.trajectory-visual > .el-empty {
  min-height: 440px;
}

.playback-bar {
  display: grid;
  grid-template-columns: 36px minmax(180px, 1fr) 70px 78px;
  align-items: center;
  gap: 12px;
  padding: 0 14px;
  border-top: 1px solid var(--line);
}

.playback-count {
  color: #5e6b66;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.trajectory-panel {
  min-height: 0;
  padding: 16px;
  overflow-y: auto;
  background: #ffffff;
  border: 1px solid #d7dedb;
}

.summary-list {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1px;
  overflow: hidden;
  background: #dfe5e2;
  border: 1px solid #dfe5e2;
}

.summary-list > div {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 11px;
  background: #f7f9f8;
}

.summary-list span {
  color: #707b77;
  font-size: 11px;
}

.summary-list strong {
  font-size: 14px;
  font-variant-numeric: tabular-nums;
}

.active-point-section {
  margin-top: 24px;
}

.point-details {
  margin: 0;
}

.point-details div {
  display: grid;
  grid-template-columns: 76px minmax(0, 1fr);
  gap: 8px;
  padding: 9px 0;
  border-bottom: 1px solid #edf0ee;
  font-size: 12px;
}

.point-details dt {
  color: #707b77;
}

.point-details dd {
  margin: 0;
  text-align: right;
  font-variant-numeric: tabular-nums;
}
</style>

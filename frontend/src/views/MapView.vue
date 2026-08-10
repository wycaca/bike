<script setup lang="ts">
import { Refresh, WarningFilled } from '@element-plus/icons-vue'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

import { errorMessage } from '@/api/http'
import { getMapVehicles } from '@/api/vehicle'
import VehicleDetailDrawer from '@/components/VehicleDetailDrawer.vue'
import VehicleMap from '@/components/VehicleMap.vue'
import { useAppStore } from '@/stores/app'
import type { CityDefinition, CoordinateSystem, LifecycleStatus, MapMarker } from '@/types/vehicle'
import {
  CITIES,
  formatTime,
  lifecycleLabels,
  markerCondition,
  vehicleCondition,
} from '@/utils/vehicle'

const appStore = useAppStore()
const markers = ref<MapMarker[]>([])
const loading = ref(false)
const error = ref('')
const zoom = ref(12)
const onlineFilter = ref<'all' | 'online' | 'offline'>('all')
const lifecycleStatus = ref<LifecycleStatus | ''>('')
const selectedVehicleId = ref<string | null>(null)
const drawerVisible = ref(false)
const coordinateSystem = ref<CoordinateSystem>('GCJ02')
const clustered = ref(false)
const lastUpdated = ref<Date | null>(null)
const currentBounds = ref<CityDefinition['bounds']>([116.2, 39.8, 116.6, 40.1])
let requestController: AbortController | null = null
let refreshTimer: number | null = null
let queryTimer: number | null = null

const city = computed(
  () => CITIES.find((item) => item.code === appStore.cityCode) ?? CITIES[0]!,
)
const totalVehicles = computed(() =>
  markers.value.reduce((total, marker) => total + marker.vehicleCount, 0),
)
const lowBatteryCount = computed(() =>
  markers.value.reduce((total, marker) => total + marker.lowBatteryCount, 0),
)
const faultCount = computed(() =>
  markers.value.reduce((total, marker) => total + marker.faultCount, 0),
)
const offlineCount = computed(() => {
  if (clustered.value) return null
  return markers.value.filter((marker) => marker.latestState && !marker.latestState.online).length
})
const abnormalVehicles = computed(() =>
  markers.value
    .filter((marker) => {
      const condition = markerCondition(marker)
      return marker.vehicleId && ['fault', 'low-battery', 'offline'].includes(condition.key)
    })
    .slice(0, 6),
)

async function loadMap() {
  requestController?.abort()
  requestController = new AbortController()
  loading.value = true
  error.value = ''
  const [minLongitude, minLatitude, maxLongitude, maxLatitude] = currentBounds.value
  try {
    const result = await getMapVehicles(
      {
        minLongitude,
        minLatitude,
        maxLongitude,
        maxLatitude,
        zoom: zoom.value,
        online:
          onlineFilter.value === 'all' ? undefined : onlineFilter.value === 'online',
        coordinateSystem: 'GCJ02',
        lifecycleStatus: lifecycleStatus.value || undefined,
      },
      requestController.signal,
    )
    markers.value = result.markers
    coordinateSystem.value = result.coordinateSystem
    clustered.value = result.clustered
    lastUpdated.value = new Date()
  } catch (cause) {
    const message = errorMessage(cause)
    if (message) error.value = message
  } finally {
    loading.value = false
  }
}

function scheduleLoad() {
  if (queryTimer) window.clearTimeout(queryTimer)
  queryTimer = window.setTimeout(loadMap, 220)
}

function selectVehicle(vehicleId: string) {
  selectedVehicleId.value = vehicleId
  drawerVisible.value = true
}

function changeCity(code: '110000' | '310000') {
  appStore.cityCode = code
  const nextCity = CITIES.find((item) => item.code === code) ?? CITIES[0]!
  currentBounds.value = [...nextCity.bounds]
  selectedVehicleId.value = null
  scheduleLoad()
}

function changeZoom(value: number) {
  zoom.value = value
  scheduleLoad()
}

function changeViewport(bounds: CityDefinition['bounds'], value: number) {
  currentBounds.value = bounds
  zoom.value = value
  scheduleLoad()
}

function startRefresh() {
  if (refreshTimer) window.clearInterval(refreshTimer)
  refreshTimer = window.setInterval(() => {
    if (!document.hidden) void loadMap()
  }, 15_000)
}

watch([onlineFilter, lifecycleStatus], scheduleLoad)
onMounted(() => {
  currentBounds.value = [...city.value.bounds]
  void loadMap()
  startRefresh()
})
onBeforeUnmount(() => {
  requestController?.abort()
  if (refreshTimer) window.clearInterval(refreshTimer)
  if (queryTimer) window.clearTimeout(queryTimer)
})
</script>

<template>
  <div class="page-view map-page">
    <div class="map-heading">
      <div class="page-heading">
        <div>
          <h1>车辆监控地图</h1>
          <p>查看当前视野内车辆位置和运行状态</p>
        </div>
        <div class="map-heading-meta">
          <span>{{ clustered ? '聚合视图' : '单车视图' }}</span>
          <span>最后更新 {{ lastUpdated ? formatTime(lastUpdated.toISOString()) : '--' }}</span>
        </div>
      </div>
    </div>

    <div class="toolbar-band map-toolbar">
      <el-radio-group :model-value="appStore.cityCode" @change="changeCity">
        <el-radio-button v-for="item in CITIES" :key="item.code" :value="item.code">
          {{ item.name }}
        </el-radio-button>
      </el-radio-group>

      <el-select v-model="onlineFilter" aria-label="在线状态" style="width: 120px">
        <el-option label="全部在线状态" value="all" />
        <el-option label="仅在线" value="online" />
        <el-option label="仅离线" value="offline" />
      </el-select>

      <el-select
        v-model="lifecycleStatus"
        clearable
        placeholder="全部生命周期"
        aria-label="生命周期"
        style="width: 140px"
      >
        <el-option
          v-for="(label, value) in lifecycleLabels"
          :key="value"
          :label="label"
          :value="value"
        />
      </el-select>

      <el-button :icon="Refresh" :loading="loading" @click="loadMap">刷新</el-button>

      <el-alert
        v-if="error"
        class="toolbar-error"
        :title="error"
        type="error"
        show-icon
        closable
        @close="error = ''"
      />
    </div>

    <div class="map-workspace">
      <VehicleMap
        :markers="markers"
        :city="city"
        :zoom="zoom"
        :coordinate-system="coordinateSystem"
        :selected-vehicle-id="selectedVehicleId"
        :loading="loading"
        @select="selectVehicle"
        @zoom-change="changeZoom"
        @viewport-change="changeViewport"
      />

      <aside class="map-side-panel">
        <section>
          <h2 class="section-title">当前视野</h2>
          <div class="map-stats">
            <div>
              <span>车辆数</span>
              <strong>{{ totalVehicles }}</strong>
            </div>
            <div>
              <span>低电量</span>
              <strong class="warning-number">{{ lowBatteryCount }}</strong>
            </div>
            <div>
              <span>故障</span>
              <strong class="danger-number">{{ faultCount }}</strong>
            </div>
            <div>
              <span>离线</span>
              <strong>{{ offlineCount ?? '--' }}</strong>
            </div>
          </div>
        </section>

        <section class="panel-section">
          <h2 class="section-title">状态图例</h2>
          <div class="map-legend">
            <span><i style="background: #198754" />正常</span>
            <span><i style="background: #236fb5" />骑行中</span>
            <span><i style="background: #d68a17" />低电量</span>
            <span><i style="background: #d34444" />故障</span>
            <span><i style="background: #737b78" />离线</span>
          </div>
        </section>

        <section class="panel-section abnormal-section">
          <h2 class="section-title">
            <el-icon><WarningFilled /></el-icon>
            异常车辆
          </h2>
          <el-empty
            v-if="abnormalVehicles.length === 0"
            :image-size="54"
            description="当前视野无异常"
          />
          <button
            v-for="marker in abnormalVehicles"
            v-else
            :key="marker.markerId"
            type="button"
            class="abnormal-vehicle"
            @click="selectVehicle(marker.vehicleId!)"
          >
            <span
              class="state-dot"
              :style="{ backgroundColor: markerCondition(marker).color }"
            />
            <span>
              <strong>{{ marker.vehicleId }}</strong>
              <small>{{ vehicleCondition(marker.latestState).label }}</small>
            </span>
            <b>{{ marker.batteryPercent ?? '--' }}%</b>
          </button>
        </section>

        <div class="coordinate-notice">
          数据坐标系: {{ coordinateSystem }}
        </div>
      </aside>
    </div>

    <VehicleDetailDrawer
      v-model="drawerVisible"
      :vehicle-id="selectedVehicleId"
    />
  </div>
</template>

<style scoped>
.map-page {
  display: grid;
  grid-template-rows: 72px 54px minmax(0, 1fr);
}

.map-heading {
  display: flex;
  align-items: center;
  padding: 0 18px;
  background: #ffffff;
  border-bottom: 1px solid var(--line);
}

.map-heading .page-heading {
  width: 100%;
}

.map-heading-meta {
  display: flex;
  gap: 16px;
  color: #6b7772;
  font-size: 12px;
}

.map-toolbar {
  position: relative;
}

.toolbar-error {
  width: auto;
  margin-left: auto;
  padding: 5px 10px;
}

.map-workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 268px;
  min-height: 0;
}

.map-side-panel {
  min-height: 0;
  padding: 16px;
  overflow-y: auto;
  background: #ffffff;
  border-left: 1px solid var(--line);
}

.map-stats {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1px;
  overflow: hidden;
  background: #dfe5e2;
  border: 1px solid #dfe5e2;
  border-radius: 4px;
}

.map-stats > div {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 10px 11px;
  background: #f7f9f8;
}

.map-stats span {
  color: #6b7772;
  font-size: 11px;
}

.map-stats strong {
  font-size: 20px;
  font-variant-numeric: tabular-nums;
}

.warning-number {
  color: #b56d08;
}

.danger-number {
  color: #c23b3b;
}

.panel-section {
  margin-top: 22px;
}

.map-legend {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px 8px;
  color: #4f5d57;
  font-size: 12px;
}

.map-legend span {
  display: flex;
  align-items: center;
  gap: 7px;
}

.map-legend i {
  width: 9px;
  height: 9px;
  border-radius: 50%;
}

.abnormal-section .section-title {
  display: flex;
  align-items: center;
  gap: 6px;
}

.abnormal-vehicle {
  display: grid;
  grid-template-columns: 16px minmax(0, 1fr) auto;
  align-items: center;
  width: 100%;
  min-height: 48px;
  padding: 7px 4px;
  color: #25332d;
  text-align: left;
  background: transparent;
  border: 0;
  border-bottom: 1px solid #edf0ee;
  cursor: pointer;
}

.abnormal-vehicle:hover {
  background: #f5f8f6;
}

.abnormal-vehicle > span:nth-child(2) {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.abnormal-vehicle strong {
  font-size: 12px;
}

.abnormal-vehicle small {
  color: #707b77;
}

.abnormal-vehicle b {
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.coordinate-notice {
  margin-top: 20px;
  padding-top: 12px;
  color: #7b8581;
  border-top: 1px solid #edf0ee;
  font-size: 11px;
}
</style>

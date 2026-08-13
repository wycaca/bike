<script setup lang="ts">
import { showToast } from 'vant'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

import { errorText, getMapVehicles, getVehicles } from '@/api'
import MobileVehicleMap from '@/components/MobileVehicleMap.vue'
import { scanVehicleCode } from '@/bridge'
import { useAppStore } from '@/stores/app'
import type { MapMarker, Vehicle } from '@/types'

type ViewMode = 'map' | 'list'
type OnlineFilter = 'all' | 'online' | 'offline'

const app = useAppStore()
const viewMode = ref<ViewMode>('map')
const keyword = ref('')
const vehicles = ref<Vehicle[]>([])
const markers = ref<MapMarker[]>([])
const loading = ref(false)
const mapLoading = ref(false)
const onlineFilter = ref<OnlineFilter>('all')
const zoom = ref(12)
const bounds = ref<[number, number, number, number]>([...app.currentCity.bounds])
const selectedMarker = ref<MapMarker | null>(null)
const detailVisible = ref(false)
let mapController: AbortController | null = null
let viewportTimer: number | null = null

const city = computed(() => app.currentCity)
const vehicleCount = computed(() => markers.value.reduce((total, marker) => total + marker.vehicleCount, 0))
const abnormalCount = computed(() => markers.value.reduce((total, marker) => total + marker.faultCount + marker.lowBatteryCount, 0))

/** 输入: 城市与可选车辆编号; 输出: 符合条件的车辆列表。 */
async function loadVehicles() {
  loading.value = true
  try {
    vehicles.value = await getVehicles(app.cityCode, keyword.value.trim())
  } catch (error) {
    showToast(errorText(error))
  } finally {
    loading.value = false
  }
}

/** 输入: 当前地图视野和在线筛选; 输出: 车辆位置或聚合标记。 */
async function loadMap() {
  mapController?.abort()
  mapController = new AbortController()
  mapLoading.value = true
  const [minLongitude, minLatitude, maxLongitude, maxLatitude] = bounds.value
  try {
    const result = await getMapVehicles({
      minLongitude,
      minLatitude,
      maxLongitude,
      maxLatitude,
      zoom: zoom.value,
      online: onlineFilter.value === 'all' ? undefined : onlineFilter.value === 'online',
      coordinateSystem: 'GCJ02',
    }, mapController.signal)
    markers.value = result.markers
  } catch (error) {
    if (!(error instanceof DOMException && error.name === 'AbortError')) showToast(errorText(error))
  } finally {
    mapLoading.value = false
  }
}

/** 输入: 用户拖动或缩放后的地图视野; 输出: 防抖刷新该区域车辆。 */
function changeViewport(nextBounds: [number, number, number, number], nextZoom: number) {
  bounds.value = nextBounds
  zoom.value = nextZoom
  if (viewportTimer) window.clearTimeout(viewportTimer)
  viewportTimer = window.setTimeout(loadMap, 250)
}

/** 输入: 地图单车标记; 输出: 展示车辆状态详情。 */
function showVehicle(marker: MapMarker) {
  if (!marker.vehicleId) return
  selectedMarker.value = marker
  detailVisible.value = true
}

/** 输入: 地图或列表模式; 输出: 切换视图并按需加载对应数据。 */
function switchView(mode: ViewMode) {
  if (viewMode.value === mode) return
  viewMode.value = mode
  if (mode === 'list' && vehicles.value.length === 0) void loadVehicles()
  if (mode === 'map' && markers.value.length === 0) void loadMap()
}

/** 输入: 原生扫码结果; 输出: 自动填入车辆编号并发起列表查询。 */
async function scan() {
  try {
    keyword.value = await scanVehicleCode()
    await loadVehicles()
  } catch (error) {
    showToast(errorText(error))
  }
}

watch(() => app.cityCode, () => {
  bounds.value = [...city.value.bounds]
  selectedMarker.value = null
  if (viewMode.value === 'map') void loadMap()
  else void loadVehicles()
})
watch(onlineFilter, loadMap)

onMounted(loadMap)
onBeforeUnmount(() => {
  mapController?.abort()
  if (viewportTimer) window.clearTimeout(viewportTimer)
})
</script>

<template>
  <div class="vehicle-page">
    <div class="view-mode-switch" role="tablist" aria-label="车辆查看方式">
      <button type="button" role="tab" :aria-selected="viewMode === 'map'" :class="{ active: viewMode === 'map' }" data-test="vehicle-map-mode" @click="switchView('map')">
        <van-icon name="location-o" /> 地图
      </button>
      <button type="button" role="tab" :aria-selected="viewMode === 'list'" :class="{ active: viewMode === 'list' }" data-test="vehicle-list-mode" @click="switchView('list')">
        <van-icon name="orders-o" /> 列表
      </button>
    </div>

    <template v-if="viewMode === 'map'">
      <div class="map-toolbar">
        <label>
          <span>在线状态</span>
          <select v-model="onlineFilter" data-test="map-online-filter">
            <option value="all">全部车辆</option>
            <option value="online">仅在线</option>
            <option value="offline">仅离线</option>
          </select>
        </label>
        <div class="map-summary"><strong>{{ vehicleCount }}</strong><span>辆车</span><b>{{ abnormalCount }}</b><span>项异常</span></div>
        <button type="button" class="icon-command" aria-label="刷新车辆位置" data-test="map-refresh" @click="loadMap"><van-icon name="replay" /></button>
      </div>

      <MobileVehicleMap
        :markers="markers"
        :center="city.center"
        :zoom="zoom"
        :loading="mapLoading"
        @select="showVehicle"
        @viewport-change="changeViewport"
      />

      <div class="map-legend" aria-label="车辆状态图例">
        <span><i class="normal" />正常</span><span><i class="riding" />骑行</span><span><i class="warning" />低电</span><span><i class="danger" />故障</span><span><i class="offline" />离线</span>
      </div>
    </template>

    <template v-else>
      <div class="search-tools">
        <van-field v-model="keyword" clearable placeholder="车辆编号或车牌" data-test="vehicle-keyword" @keyup.enter="loadVehicles">
          <template #left-icon><van-icon name="search" /></template>
        </van-field>
        <van-button square type="primary" aria-label="搜索" data-test="vehicle-search" @click="loadVehicles"><van-icon name="search" /></van-button>
        <van-button square plain type="primary" aria-label="扫码" data-test="vehicle-scan" @click="scan"><van-icon name="scan" /></van-button>
      </div>
      <div class="section-head"><h2>车辆状态</h2><span>{{ vehicles.length }} 辆</span></div>
      <div v-if="vehicles.length" class="vehicle-list">
        <article v-for="vehicle in vehicles" :key="vehicle.vehicleId" class="vehicle-row">
          <div><strong>{{ vehicle.vehicleId }}</strong><span>{{ vehicle.plateNumber || '未绑定车牌' }}</span></div>
          <div class="vehicle-state">
            <b>{{ vehicle.latestState?.batteryPercent ?? '--' }}%</b>
            <span :class="{ offline: !vehicle.latestState?.online }">{{ vehicle.latestState?.online ? '在线' : '离线' }}</span>
          </div>
        </article>
      </div>
      <van-loading v-else-if="loading" class="center-loading" />
      <div v-else class="empty-state">没有找到车辆</div>
    </template>

    <van-popup v-model:show="detailVisible" position="bottom" safe-area-inset-bottom class="vehicle-map-detail">
      <div class="sheet-head"><h3>{{ selectedMarker?.vehicleId }}</h3><span>{{ selectedMarker?.lifecycleStatus || '状态未知' }}</span></div>
      <van-cell title="电量" :value="selectedMarker?.batteryPercent === null ? '--' : `${selectedMarker?.batteryPercent}%`" />
      <van-cell title="连接状态" :value="selectedMarker?.latestState?.online ? '在线' : '离线'" />
      <van-cell title="控制器" :value="selectedMarker?.latestState?.controllerStatus || '--'" />
      <van-cell title="车辆位置" :value="selectedMarker ? `${selectedMarker.longitude.toFixed(5)}, ${selectedMarker.latitude.toFixed(5)}` : '--'" />
    </van-popup>
  </div>
</template>

<style scoped>
.vehicle-page { display: flex; flex-direction: column; gap: 10px; }
.view-mode-switch { display: grid; grid-template-columns: 1fr 1fr; height: 40px; padding: 3px; background: #dfe6e2; }
.view-mode-switch button { display: flex; align-items: center; justify-content: center; gap: 5px; color: var(--muted); border: 0; background: transparent; }
.view-mode-switch button.active { color: var(--brand-dark); background: #fff; box-shadow: 0 1px 3px rgb(20 40 32 / 10%); font-weight: 600; }
.map-toolbar { display: grid; grid-template-columns: minmax(110px, 1fr) auto 38px; min-height: 42px; align-items: center; gap: 8px; }
.map-toolbar label { display: flex; min-width: 0; flex-direction: column; gap: 2px; }
.map-toolbar label span { color: var(--muted); font-size: 10px; }
.map-toolbar select { width: 100%; padding: 2px 0; color: #17231f; border: 0; outline: 0; background: transparent; font-size: 13px; }
.map-summary { display: flex; align-items: baseline; gap: 3px; white-space: nowrap; }
.map-summary strong, .map-summary b { font-size: 16px; }
.map-summary b { margin-left: 5px; color: var(--danger); }
.map-summary span { color: var(--muted); font-size: 10px; }
.icon-command { display: grid; width: 38px; height: 38px; place-items: center; padding: 0; color: var(--brand); border: 1px solid var(--line); background: #fff; font-size: 18px; }
.map-legend { display: flex; align-items: center; justify-content: space-around; min-height: 28px; color: var(--muted); font-size: 10px; }
.map-legend span { display: flex; align-items: center; gap: 4px; }
.map-legend i { width: 8px; height: 8px; border-radius: 50%; }
.map-legend .normal { background: #176448; }.map-legend .riding { background: #2673a8; }.map-legend .warning { background: #c77a16; }.map-legend .danger { background: #c6423a; }.map-legend .offline { background: #68736f; }
.vehicle-map-detail { padding-bottom: 12px; }
</style>

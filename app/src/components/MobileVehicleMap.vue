<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

import { amapConfigured, loadAmap } from '@/amap'
import type { MapMarker } from '@/types'

const props = defineProps<{
  markers: MapMarker[]
  center: [number, number]
  zoom: number
  loading: boolean
}>()

const emit = defineEmits<{
  select: [marker: MapMarker]
  viewportChange: [bounds: [number, number, number, number], zoom: number]
}>()

const container = ref<HTMLDivElement | null>(null)
const error = ref('')
let map: AMap.Map | null = null
let mapMarkers: AMap.Marker[] = []

/** 输入: 单车或聚合标记; 输出: 对应运营状态颜色。 */
function markerColor(marker: MapMarker): string {
  if (marker.faultCount > 0 || marker.latestState?.controllerStatus === 'FAULT') return '#c6423a'
  if (marker.lowBatteryCount > 0 || (marker.batteryPercent !== null && marker.batteryPercent <= 20)) return '#c77a16'
  if (marker.latestState && !marker.latestState.online) return '#68736f'
  if (marker.latestState?.rideStatus === 'RIDING') return '#2673a8'
  return '#176448'
}

/** 输入: 被点击的地图标记; 输出: 聚合点继续放大，单车标记打开详情。 */
function activateMarker(marker: MapMarker) {
  if (marker.markerType === 'CLUSTER') {
    map?.setZoomAndCenter(Math.min(18, Math.round(map.getZoom()) + 2), [marker.longitude, marker.latitude])
    return
  }
  emit('select', marker)
}

/** 输入: 地图标记; 输出: 高德 Marker 使用的可点击 DOM 节点。 */
function createMarkerElement(marker: MapMarker): HTMLButtonElement {
  const element = document.createElement('button')
  element.type = 'button'
  element.className = marker.markerType === 'CLUSTER' ? 'mobile-map-marker cluster' : 'mobile-map-marker'
  element.style.backgroundColor = markerColor(marker)
  element.textContent = marker.markerType === 'CLUSTER' ? String(marker.vehicleCount) : '车'
  element.setAttribute('aria-label', marker.vehicleId ?? `${marker.vehicleCount} 辆车`)
  element.addEventListener('click', (event) => {
    event.stopPropagation()
    activateMarker(marker)
  })
  return element
}

/** 输入: 最新标记数组; 输出: 清理旧覆盖物并在地图上绘制新覆盖物。 */
function renderMarkers() {
  if (!map) return
  map.remove(mapMarkers)
  mapMarkers = props.markers.map((marker) => {
    const overlay = new AMap.Marker({
      position: [marker.longitude, marker.latitude],
      content: createMarkerElement(marker),
      offset: new AMap.Pixel(marker.markerType === 'CLUSTER' ? -20 : -17, marker.markerType === 'CLUSTER' ? -20 : -17),
    })
    return overlay
  })
  map.add(mapMarkers)
}

/** 输入: 当前高德地图视野; 输出: 四角边界及整数缩放级别。 */
function emitViewport() {
  if (!map) return
  const bounds = map.getBounds()
  const southWest = bounds.getSouthWest()
  const northEast = bounds.getNorthEast()
  emit('viewportChange', [southWest.getLng(), southWest.getLat(), northEast.getLng(), northEast.getLat()], Math.round(map.getZoom()))
}

/** 输入: 地图容器和高德配置; 输出: 可交互地图或明确的配置错误。 */
async function initializeMap() {
  if (!container.value || map) return
  if (!amapConfigured()) {
    error.value = '未配置高德地图 Key'
    return
  }
  try {
    await loadAmap()
    map = new AMap.Map(container.value, {
      center: props.center,
      zoom: props.zoom,
      mapStyle: 'amap://styles/normal',
      viewMode: '2D',
    })
    map.on('moveend', emitViewport)
    map.on('zoomend', emitViewport)
    renderMarkers()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '高德地图加载失败'
  }
}

watch(() => props.markers, renderMarkers, { deep: true })
watch(() => props.center, (center) => map?.setZoomAndCenter(props.zoom, center))

onMounted(initializeMap)
onBeforeUnmount(() => {
  map?.destroy()
  map = null
})
</script>

<template>
  <div class="mobile-vehicle-map">
    <div ref="container" class="mobile-amap-container" data-test="vehicle-map" />
    <div v-if="error" class="mobile-map-error">
      <van-icon name="warning-o" />
      <span>{{ error }}</span>
    </div>
    <div v-if="loading" class="mobile-map-loading">
      <van-loading size="18" />
      <span>刷新车辆位置</span>
    </div>
  </div>
</template>

<style scoped>
.mobile-vehicle-map {
  position: relative;
  width: 100%;
  height: clamp(390px, calc(100vh - 226px - env(safe-area-inset-top) - env(safe-area-inset-bottom)), 720px);
  min-height: 390px;
  overflow: hidden;
  border: 1px solid var(--line);
  background: #dfe7e3;
}

.mobile-amap-container { width: 100%; height: 100%; }
.mobile-map-loading, .mobile-map-error { position: absolute; z-index: 5; display: flex; align-items: center; gap: 7px; padding: 8px 10px; background: rgb(255 255 255 / 94%); font-size: 12px; }
.mobile-map-loading { top: 10px; right: 10px; }
.mobile-map-error { right: 12px; bottom: 12px; left: 12px; color: var(--danger); }

:global(.mobile-map-marker) {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  padding: 0;
  color: #fff;
  border: 2px solid #fff;
  border-radius: 50%;
  box-shadow: 0 2px 7px rgb(20 38 31 / 28%);
  font-size: 12px;
  font-weight: 700;
}

:global(.mobile-map-marker.cluster) { width: 40px; height: 40px; }
</style>

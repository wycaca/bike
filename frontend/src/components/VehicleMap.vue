<script setup lang="ts">
import { Bicycle } from '@element-plus/icons-vue'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

import type { CityDefinition, MapMarker } from '@/types/vehicle'
import { amapConfigured, loadAmap } from '@/utils/amap'
import { markerCondition, projectCoordinate } from '@/utils/vehicle'

const props = defineProps<{
  markers: MapMarker[]
  city: CityDefinition
  zoom: number
  coordinateSystem: 'WGS84' | 'GCJ02'
  selectedVehicleId: string | null
  loading: boolean
}>()

const emit = defineEmits<{
  select: [vehicleId: string]
  'zoom-change': [zoom: number]
  'viewport-change': [bounds: CityDefinition['bounds'], zoom: number]
}>()

const container = ref<HTMLDivElement | null>(null)
const amapReady = ref(false)
const mapError = ref('')
const canUseAmap = computed(() => amapConfigured() && props.coordinateSystem === 'GCJ02')
let map: AMap.Map | null = null
let mapMarkers: AMap.Marker[] = []

const previewMarkers = computed(() =>
  props.markers.map((marker) => ({
    marker,
    position: projectCoordinate(marker.longitude, marker.latitude, props.city.bounds),
    condition: markerCondition(marker),
  })),
)

function emitViewport() {
  if (!map) return
  const bounds = map.getBounds()
  const southWest = bounds.getSouthWest()
  const northEast = bounds.getNorthEast()
  emit(
    'viewport-change',
    [southWest.getLng(), southWest.getLat(), northEast.getLng(), northEast.getLat()],
    Math.round(map.getZoom()),
  )
}

function createMarkerElement(marker: MapMarker) {
  const element = document.createElement('button')
  const condition = markerCondition(marker)
  element.type = 'button'
  element.className = `amap-vehicle-marker ${
    marker.vehicleId === props.selectedVehicleId ? 'is-selected' : ''
  }`
  element.style.backgroundColor = condition.color
  element.textContent = marker.markerType === 'CLUSTER' ? String(marker.vehicleCount) : '车'
  element.setAttribute('aria-label', marker.vehicleId ?? `${marker.vehicleCount} 辆车辆`)
  return element
}

function renderAmapMarkers() {
  if (!map) return
  map.remove(mapMarkers)
  mapMarkers = props.markers.map((marker) => {
    const mapMarker = new AMap.Marker({
      position: [marker.longitude, marker.latitude],
      content: createMarkerElement(marker),
      offset: new AMap.Pixel(-16, -16),
    })
    if (marker.vehicleId) {
      mapMarker.on('click', () => emit('select', marker.vehicleId as string))
    }
    return mapMarker
  })
  map.add(mapMarkers)
}

async function initializeAmap() {
  if (!canUseAmap.value || !container.value || map) return
  mapError.value = ''
  try {
    await loadAmap()
    map = new AMap.Map(container.value, {
      center: props.city.center,
      zoom: props.zoom,
      mapStyle: 'amap://styles/normal',
    })
    map.on('moveend', emitViewport)
    map.on('zoomend', emitViewport)
    amapReady.value = true
    renderAmapMarkers()
  } catch (cause) {
    mapError.value = cause instanceof Error ? cause.message : '高德地图加载失败'
    map?.destroy()
    map = null
    amapReady.value = false
  }
}

/** 输入: 用户重试动作; 输出: 清除错误并重新加载高德地图。 */
async function retryAmap(): Promise<void> {
  mapError.value = ''
  await initializeAmap()
}

function selectPreviewMarker(marker: MapMarker) {
  if (marker.vehicleId) emit('select', marker.vehicleId)
}

function changePreviewZoom(step: number) {
  emit('zoom-change', Math.min(18, Math.max(10, props.zoom + step)))
}

watch(
  () => props.city.code,
  () => {
    if (map) map.setZoomAndCenter(props.zoom, props.city.center)
  },
)
watch(() => [props.markers, props.selectedVehicleId], renderAmapMarkers, { deep: true })
watch(canUseAmap, async () => {
  await nextTick()
  await initializeAmap()
})

onMounted(initializeAmap)
onBeforeUnmount(() => {
  map?.destroy()
  map = null
})
</script>

<template>
  <div class="vehicle-map" :class="{ 'is-amap': amapReady }">
    <div v-show="canUseAmap" ref="container" class="amap-container" />

    <div v-if="!amapReady" class="coordinate-preview">
      <div class="preview-grid" aria-hidden="true">
        <span v-for="index in 8" :key="`vertical-${index}`" class="grid-line vertical" :style="{ left: `${index * 11}%` }" />
        <span v-for="index in 6" :key="`horizontal-${index}`" class="grid-line horizontal" :style="{ top: `${index * 14}%` }" />
        <span class="road road-one" />
        <span class="road road-two" />
        <span class="road road-three" />
      </div>

      <button
        v-for="item in previewMarkers"
        :key="item.marker.markerId"
        class="preview-marker"
        :class="{
          cluster: item.marker.markerType === 'CLUSTER',
          selected: item.marker.vehicleId === selectedVehicleId,
        }"
        :style="{
          left: `${item.position.left}%`,
          top: `${item.position.top}%`,
          backgroundColor: item.condition.color,
        }"
        :aria-label="item.marker.vehicleId ?? `${item.marker.vehicleCount} 辆车辆`"
        type="button"
        @click="selectPreviewMarker(item.marker)"
      >
        <span v-if="item.marker.markerType === 'CLUSTER'">{{ item.marker.vehicleCount }}</span>
        <el-icon v-else><Bicycle /></el-icon>
      </button>

      <div class="preview-label">
        <strong>{{ city.name }}</strong>
        <span>{{ coordinateSystem }} 坐标预览</span>
      </div>

      <div class="map-zoom-controls">
        <el-button aria-label="放大地图" @click="changePreviewZoom(1)">+</el-button>
        <el-button aria-label="缩小地图" @click="changePreviewZoom(-1)">-</el-button>
      </div>

      <el-alert
        v-if="mapError"
        class="map-error"
        :title="mapError"
        type="error"
        show-icon
        :closable="false"
      >
        <el-button size="small" @click="retryAmap">重试地图</el-button>
      </el-alert>
    </div>

    <div v-if="loading" class="map-loading" aria-live="polite">
      <span class="loading-spinner" />
      正在刷新车辆位置
    </div>
  </div>
</template>

<style scoped>
.vehicle-map,
.amap-container,
.coordinate-preview {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 420px;
}

.vehicle-map {
  overflow: hidden;
  background: #dfe7e3;
}

.amap-container {
  z-index: 1;
}

.coordinate-preview {
  position: absolute;
  inset: 0;
  z-index: 2;
  background: #e7ece9;
}

.preview-grid,
.grid-line,
.road {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.grid-line {
  background: #d0d9d4;
}

.grid-line.vertical {
  width: 1px;
}

.grid-line.horizontal {
  height: 1px;
}

.road {
  height: 9px;
  background: #f8faf9;
  border: 1px solid #c8d2cd;
  transform-origin: center;
}

.road-one {
  top: 31%;
  left: -8%;
  width: 116%;
  transform: rotate(8deg);
}

.road-two {
  top: 62%;
  left: -5%;
  width: 110%;
  transform: rotate(-13deg);
}

.road-three {
  top: 4%;
  left: 58%;
  width: 84%;
  transform: rotate(73deg);
}

.preview-marker {
  position: absolute;
  z-index: 3;
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  padding: 0;
  color: #ffffff;
  border: 2px solid #ffffff;
  border-radius: 50%;
  box-shadow: 0 2px 7px rgb(19 35 29 / 24%);
  transform: translate(-50%, -50%);
  cursor: pointer;
}

.preview-marker:hover,
.preview-marker.selected {
  z-index: 4;
  outline: 3px solid rgb(19 35 29 / 24%);
  transform: translate(-50%, -50%) scale(1.12);
}

.preview-marker.cluster {
  width: 38px;
  height: 38px;
  font-size: 13px;
  font-weight: 700;
}

.preview-label {
  position: absolute;
  top: 16px;
  left: 16px;
  z-index: 4;
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 9px 11px;
  background: rgb(255 255 255 / 92%);
  border: 1px solid #ccd6d1;
  border-radius: 4px;
}

.preview-label strong {
  font-size: 14px;
}

.preview-label span {
  color: #69746f;
  font-size: 11px;
}

.map-zoom-controls {
  position: absolute;
  right: 16px;
  bottom: 18px;
  z-index: 4;
  display: flex;
  flex-direction: column;
  gap: 1px;
  box-shadow: 0 2px 8px rgb(30 45 39 / 16%);
}

.map-zoom-controls .el-button {
  width: 34px;
  height: 34px;
  margin: 0;
  padding: 0;
  font-size: 19px;
  border-radius: 0;
}

.map-error {
  position: absolute;
  right: 16px;
  bottom: 82px;
  z-index: 5;
  width: 360px;
}

.map-loading {
  position: absolute;
  top: 16px;
  right: 16px;
  z-index: 6;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 11px;
  color: #35423d;
  background: rgb(255 255 255 / 92%);
  border: 1px solid #d2dad6;
  border-radius: 4px;
  font-size: 12px;
}

.loading-spinner {
  width: 13px;
  height: 13px;
  border: 2px solid #b9c8c1;
  border-top-color: #146c4d;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

:global(.amap-vehicle-marker) {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  padding: 0;
  color: #ffffff;
  border: 2px solid #ffffff;
  border-radius: 50%;
  box-shadow: 0 2px 7px rgb(19 35 29 / 25%);
  cursor: pointer;
}

:global(.amap-vehicle-marker.is-selected) {
  outline: 3px solid rgb(19 35 29 / 24%);
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>

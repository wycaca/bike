<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

import type { TrajectoryPoint } from '@/types/vehicle'
import { amapConfigured, loadAmap } from '@/utils/amap'

const props = defineProps<{
  points: TrajectoryPoint[]
  activeIndex: number
}>()

const padding = 6
const container = ref<HTMLDivElement | null>(null)
const amapReady = ref(false)
const mapError = ref('')
const canUseAmap = computed(
  () => amapConfigured() && props.points[0]?.coordinateSystem === 'GCJ02',
)
let map: AMap.Map | null = null
let activeMarker: AMap.Marker | null = null

const projectedPoints = computed(() => {
  if (props.points.length === 0) return []
  const longitudes = props.points.map((point) => point.longitude)
  const latitudes = props.points.map((point) => point.latitude)
  const minLongitude = Math.min(...longitudes)
  const maxLongitude = Math.max(...longitudes)
  const minLatitude = Math.min(...latitudes)
  const maxLatitude = Math.max(...latitudes)
  const longitudeRange = maxLongitude - minLongitude || 1
  const latitudeRange = maxLatitude - minLatitude || 1

  return props.points.map((point) => ({
    x: padding + ((point.longitude - minLongitude) / longitudeRange) * (100 - padding * 2),
    y: padding + ((maxLatitude - point.latitude) / latitudeRange) * (100 - padding * 2),
  }))
})

const polyline = computed(() =>
  projectedPoints.value.map((point) => `${point.x},${point.y}`).join(' '),
)
const activePoint = computed(() => projectedPoints.value[props.activeIndex])

function renderTrajectory() {
  if (!map || props.points.length === 0) return
  map.clearMap()
  const path = props.points.map((point) => [point.longitude, point.latitude] as [number, number])
  const line = new AMap.Polyline({
    path,
    strokeColor: '#176f50',
    strokeWeight: 6,
    strokeOpacity: 0.9,
    lineJoin: 'round',
    lineCap: 'round',
  })
  const start = new AMap.CircleMarker({
    center: path[0]!,
    radius: 7,
    fillColor: '#176f50',
    fillOpacity: 1,
    strokeColor: '#ffffff',
    strokeWeight: 2,
  })
  const end = new AMap.CircleMarker({
    center: path[path.length - 1]!,
    radius: 7,
    fillColor: '#e34f3d',
    fillOpacity: 1,
    strokeColor: '#ffffff',
    strokeWeight: 2,
  })
  activeMarker = new AMap.Marker({
    position: path[Math.min(props.activeIndex, path.length - 1)]!,
    content: '<span class="trajectory-active-marker"></span>',
    offset: new AMap.Pixel(-7, -7),
    zIndex: 120,
  })
  map.add(line)
  map.add(start)
  map.add(end)
  map.add(activeMarker)
  map.setFitView([line, start, end], false, [56, 56, 56, 56], 17)
}

function updateActiveMarker() {
  const point = props.points[props.activeIndex]
  if (activeMarker && point) activeMarker.setPosition([point.longitude, point.latitude])
}

async function initializeAmap() {
  if (!canUseAmap.value || !container.value || map) return
  mapError.value = ''
  try {
    await loadAmap()
    const first = props.points[0]!
    map = new AMap.Map(container.value, {
      center: [first.longitude, first.latitude],
      zoom: 15,
      mapStyle: 'amap://styles/normal',
    })
    amapReady.value = true
    renderTrajectory()
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

watch(
  () => props.points,
  async () => {
    await nextTick()
    if (!map) await initializeAmap()
    else renderTrajectory()
  },
)
watch(() => props.activeIndex, updateActiveMarker)
watch(canUseAmap, async () => {
  await nextTick()
  await initializeAmap()
})

onMounted(initializeAmap)
onBeforeUnmount(() => {
  map?.destroy()
  map = null
  activeMarker = null
})
</script>

<template>
  <div class="trajectory-canvas">
    <div v-show="canUseAmap" ref="container" class="trajectory-map" />

    <div v-if="!amapReady" class="trajectory-preview">
      <div class="trajectory-grid" aria-hidden="true">
        <span v-for="index in 7" :key="`v-${index}`" class="trajectory-line vertical" :style="{ left: `${index * 12.5}%` }" />
        <span v-for="index in 5" :key="`h-${index}`" class="trajectory-line horizontal" :style="{ top: `${index * 16.6}%` }" />
      </div>
      <svg viewBox="0 0 100 100" preserveAspectRatio="none" role="img" aria-label="车辆轨迹坐标预览">
        <polyline v-if="points.length > 1" :points="polyline" class="trajectory-path" />
        <circle
          v-for="(point, index) in projectedPoints"
          :key="index"
          :cx="point.x"
          :cy="point.y"
          r="0.9"
          class="trajectory-node"
        />
        <circle
          v-if="activePoint"
          :cx="activePoint.x"
          :cy="activePoint.y"
          r="2.3"
          class="active-node-ring"
        />
        <circle
          v-if="activePoint"
          :cx="activePoint.x"
          :cy="activePoint.y"
          r="1.25"
          class="active-node"
        />
      </svg>
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

    <div class="coordinate-badge">
      {{ points[0]?.coordinateSystem ?? 'WGS84' }} {{ amapReady ? '高德地图' : '坐标预览' }}
    </div>
  </div>
</template>

<style scoped>
.trajectory-canvas,
.trajectory-map,
.trajectory-preview {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 440px;
}

.trajectory-canvas {
  overflow: hidden;
  background: #e6ece9;
  border: 1px solid #d4dcd8;
}

.trajectory-map {
  z-index: 1;
}

.trajectory-preview {
  position: absolute;
  inset: 0;
}

.trajectory-grid,
.trajectory-line {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.trajectory-line {
  background: #d3dcd7;
}

.trajectory-line.vertical {
  width: 1px;
}

.trajectory-line.horizontal {
  height: 1px;
}

svg {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.trajectory-path {
  fill: none;
  stroke: #176f50;
  stroke-width: 0.75;
  stroke-linecap: round;
  stroke-linejoin: round;
  vector-effect: non-scaling-stroke;
}

.trajectory-node {
  fill: #ffffff;
  stroke: #176f50;
  stroke-width: 0.45;
  vector-effect: non-scaling-stroke;
}

.active-node-ring {
  fill: rgb(23 111 80 / 22%);
  stroke: none;
}

.active-node {
  fill: #e34f3d;
  stroke: #ffffff;
  stroke-width: 0.5;
  vector-effect: non-scaling-stroke;
}

.coordinate-badge {
  position: absolute;
  top: 14px;
  left: 14px;
  z-index: 3;
  padding: 7px 9px;
  color: #52615b;
  background: rgb(255 255 255 / 90%);
  border: 1px solid #ccd6d1;
  border-radius: 4px;
  font-size: 11px;
}

.map-error {
  position: absolute;
  right: 14px;
  bottom: 14px;
  z-index: 3;
  width: min(360px, calc(100% - 28px));
}

:global(.trajectory-active-marker) {
  display: block;
  width: 14px;
  height: 14px;
  background: #e34f3d;
  border: 3px solid #ffffff;
  border-radius: 50%;
  box-shadow: 0 1px 5px rgb(19 35 29 / 32%);
}
</style>

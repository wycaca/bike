<script setup lang="ts">
import { LocationFilled } from '@element-plus/icons-vue'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

import type { Coordinate, Geofence, GeoViolation, ParkingPoint } from '@/types/operations'
import type { CityDefinition } from '@/types/vehicle'
import { amapConfigured, loadAmap } from '@/utils/amap'
import { projectCoordinate } from '@/utils/vehicle'

const props = defineProps<{
  city: CityDefinition
  fences: Geofence[]
  parkingPoints: ParkingPoint[]
  violations: GeoViolation[]
  draftBoundary: Coordinate[]
  draftLocation: Coordinate | null
  drawing: boolean
}>()

const emit = defineEmits<{ 'map-click': [coordinate: Coordinate] }>()
const container = ref<HTMLDivElement | null>(null)
const ready = ref(false)
const mapError = ref('')
let map: AMap.Map | null = null
let overlays: Array<AMap.Polygon | AMap.Circle | AMap.Marker> = []

const previewFences = computed(() =>
  props.fences.map((fence) => ({
    ...fence,
    points: fence.boundary.map((point) => projectCoordinate(point.longitude, point.latitude, props.city.bounds)),
  })),
)
const previewParking = computed(() =>
  props.parkingPoints.map((point) => ({
    ...point,
    position: projectCoordinate(point.location.longitude, point.location.latitude, props.city.bounds),
  })),
)

/** 输入: 围栏类型; 输出: 地图覆盖物颜色。 */
function fenceColor(type: Geofence['fenceType']): string {
  if (type === 'OPERATION') return '#23845f'
  if (type === 'NO_RIDE') return '#c24b4b'
  return '#d88920'
}

/** 输入: CSS 类名和文字; 输出: 高德 Marker 使用的 DOM 节点。 */
function markerContent(className: string, text: string): HTMLDivElement {
  const element = document.createElement('div')
  element.className = className
  element.textContent = text
  return element
}

/** 输入: 高德多边形配置; 输出: 绕过旧版类型包构造签名后的多边形覆盖物。 */
function createPolygon(options: object): AMap.Polygon {
  const PolygonConstructor = AMap.Polygon as unknown as new (polygonOptions: object) => AMap.Polygon
  return new PolygonConstructor(options)
}

/** 输入: 当前设施数据; 输出: 重新生成全部高德覆盖物。 */
function renderOverlays() {
  if (!map) return
  map.remove(overlays)
  overlays = []
  props.fences.forEach((fence) => {
    const color = fenceColor(fence.fenceType)
    overlays.push(createPolygon({
      path: fence.boundary.map((point) => [point.longitude, point.latitude]),
      strokeColor: color,
      strokeWeight: 2,
      fillColor: color,
      fillOpacity: 0.14,
      zIndex: 20,
    }))
  })
  props.parkingPoints.forEach((point) => {
    overlays.push(new AMap.Circle({
      center: [point.location.longitude, point.location.latitude],
      radius: point.radiusMeters,
      strokeColor: '#2876a9',
      strokeWeight: 1,
      fillColor: '#4c9ac8',
      fillOpacity: 0.14,
      zIndex: 22,
    }))
    overlays.push(new AMap.Marker({
      position: [point.location.longitude, point.location.latitude],
      content: markerContent('amap-parking-marker', 'P'),
      offset: new AMap.Pixel(-13, -13),
      title: point.pointName,
      zIndex: 30,
    }))
  })
  props.violations.forEach((violation) => {
    overlays.push(new AMap.Marker({
      position: [violation.longitude, violation.latitude],
      content: markerContent('amap-violation-marker', '!'),
      offset: new AMap.Pixel(-12, -12),
      title: violation.vehicleId,
      zIndex: 40,
    }))
  })
  if (props.draftBoundary.length) {
    overlays.push(createPolygon({
      path: props.draftBoundary.map((point) => [point.longitude, point.latitude]),
      strokeColor: '#176f50',
      strokeWeight: 3,
      strokeStyle: 'dashed',
      fillColor: '#56aa88',
      fillOpacity: 0.18,
      zIndex: 50,
    }))
  }
  if (props.draftLocation) {
    overlays.push(new AMap.Marker({
      position: [props.draftLocation.longitude, props.draftLocation.latitude],
      content: markerContent('amap-draft-marker', '+'),
      offset: new AMap.Pixel(-14, -14),
      zIndex: 55,
    }))
  }
  map.add(overlays)
}

/** 输入: 当前城市和地图容器; 输出: 初始化高德地图并绑定点选事件。 */
async function initialize() {
  if (!amapConfigured() || !container.value || map) return
  try {
    await loadAmap()
    map = new AMap.Map(container.value, {
      center: props.city.center,
      zoom: 12,
      mapStyle: 'amap://styles/normal',
    })
    map.on('click', (event: { lnglat: AMap.LngLat }) => {
      if (!props.drawing) return
      emit('map-click', { longitude: event.lnglat.getLng(), latitude: event.lnglat.getLat() })
    })
    ready.value = true
    renderOverlays()
  } catch (cause) {
    mapError.value = cause instanceof Error ? cause.message : '高德地图加载失败'
  }
}

/** 输入: 降级预览点击位置; 输出: 换算后的经纬度。 */
function previewClick(event: MouseEvent) {
  if (!props.drawing) return
  const target = event.currentTarget as HTMLElement
  const rect = target.getBoundingClientRect()
  const ratioX = (event.clientX - rect.left) / rect.width
  const ratioY = (event.clientY - rect.top) / rect.height
  const [minLng, minLat, maxLng, maxLat] = props.city.bounds
  emit('map-click', {
    longitude: minLng + ratioX * (maxLng - minLng),
    latitude: maxLat - ratioY * (maxLat - minLat),
  })
}

watch(() => props.city.code, () => map?.setZoomAndCenter(12, props.city.center))
watch(
  () => [props.fences, props.parkingPoints, props.violations, props.draftBoundary, props.draftLocation],
  renderOverlays,
  { deep: true },
)
watch(() => props.drawing, async () => { await nextTick(); await initialize() })
onMounted(initialize)
onBeforeUnmount(() => { map?.destroy(); map = null })
</script>

<template>
  <div class="geo-map" :class="{ drawing }">
    <div v-show="ready" ref="container" class="geo-amap" />
    <div v-if="!ready" class="geo-preview" @click="previewClick">
      <span class="preview-road horizontal-one" /><span class="preview-road horizontal-two" />
      <span class="preview-road vertical-one" /><span class="preview-road vertical-two" />
      <div
        v-for="fence in previewFences"
        :key="fence.fenceId"
        class="preview-fence"
        :style="{
          borderColor: fenceColor(fence.fenceType),
          left: `${Math.min(...fence.points.map((point) => point.left))}%`,
          top: `${Math.min(...fence.points.map((point) => point.top))}%`,
          width: `${Math.max(...fence.points.map((point) => point.left)) - Math.min(...fence.points.map((point) => point.left))}%`,
          height: `${Math.max(...fence.points.map((point) => point.top)) - Math.min(...fence.points.map((point) => point.top))}%`,
        }"
      />
      <span
        v-for="point in previewParking"
        :key="point.pointId"
        class="preview-parking"
        :style="{ left: `${point.position.left}%`, top: `${point.position.top}%` }"
      ><LocationFilled /></span>
      <div class="preview-city"><strong>{{ city.name }}</strong><small>空间设施预览</small></div>
      <el-alert v-if="mapError" class="geo-map-error" :title="mapError" type="error" :closable="false" />
    </div>
    <div v-if="drawing" class="drawing-hint">点击地图选取位置</div>
  </div>
</template>

<style scoped>
.geo-map { position: relative; min-width: 0; min-height: 0; overflow: hidden; background: #dbe4e0; }
.geo-amap, .geo-preview { position: absolute; inset: 0; }
.geo-map.drawing { cursor: crosshair; }
.geo-preview { overflow: hidden; background: #e8eeeb; }
.preview-road { position: absolute; background: #fff; border: 1px solid #cbd5d0; }
.horizontal-one { top: 34%; left: -3%; width: 108%; height: 18px; transform: rotate(-5deg); }
.horizontal-two { top: 68%; left: -4%; width: 110%; height: 12px; transform: rotate(4deg); }
.vertical-one { top: -4%; left: 31%; width: 15px; height: 108%; transform: rotate(5deg); }
.vertical-two { top: -4%; left: 72%; width: 11px; height: 108%; transform: rotate(-7deg); }
.preview-fence { position: absolute; min-width: 20px; min-height: 20px; background: rgb(39 132 95 / 10%); border: 2px dashed; }
.preview-parking { position: absolute; display: grid; place-items: center; width: 26px; height: 26px; color: #fff; background: #2876a9; border-radius: 50%; transform: translate(-50%, -50%); }
.preview-parking svg { width: 15px; }
.preview-city { position: absolute; top: 16px; left: 16px; display: flex; flex-direction: column; padding: 9px 12px; background: rgb(255 255 255 / 92%); border: 1px solid #d6deda; }
.preview-city small { margin-top: 2px; color: var(--muted); }
.geo-map-error { position: absolute; right: 16px; bottom: 16px; width: auto; }
.drawing-hint { position: absolute; top: 14px; left: 50%; z-index: 60; padding: 7px 12px; color: #fff; background: #174d3a; border-radius: 4px; font-size: 12px; transform: translateX(-50%); pointer-events: none; }
:global(.amap-parking-marker), :global(.amap-violation-marker), :global(.amap-draft-marker) { display: grid; place-items: center; width: 26px; height: 26px; color: #fff; background: #2876a9; border: 2px solid #fff; border-radius: 50%; box-shadow: 0 2px 5px rgb(0 0 0 / 24%); font: 700 11px/1 Arial; }
:global(.amap-violation-marker) { width: 24px; height: 24px; background: #ce3f3f; }
:global(.amap-draft-marker) { width: 28px; height: 28px; background: #176f50; font-size: 18px; }
</style>

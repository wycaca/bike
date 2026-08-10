<script setup lang="ts">
import { Check, Delete, Edit, Plus, Refresh, RefreshLeft, Remove } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, reactive, ref, watch } from 'vue'

import { disableFence, disableParkingPoint, getGeoOverview, saveFence, saveParkingPoint } from '@/api/geo'
import { errorMessage } from '@/api/http'
import GeoFacilityMap from '@/components/GeoFacilityMap.vue'
import { useAppStore } from '@/stores/app'
import type { Coordinate, FenceType, Geofence, GeoOverview, ParkingPoint } from '@/types/operations'
import { CITIES, formatTime } from '@/utils/vehicle'

const appStore = useAppStore()
const overview = ref<GeoOverview>({ fences: [], parkingPoints: [], violations: [] })
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const activeTab = ref<'facilities' | 'violations'>('facilities')
const editor = ref<'fence' | 'parking' | null>(null)
const editingId = ref<string | null>(null)
const editorVisible = ref(false)
const draftBoundary = ref<Coordinate[]>([])
const draftLocation = ref<Coordinate | null>(null)

const fenceForm = reactive({ fenceName: '', fenceType: 'OPERATION' as FenceType, orgId: 'ORG-BJ' })
const parkingForm = reactive({ pointName: '', orgId: 'ORG-BJ', radiusMeters: 300, capacity: 80 })
const city = computed(() => CITIES.find((item) => item.code === appStore.cityCode) ?? CITIES[0]!)
const drawing = computed(() => editorVisible.value && editor.value !== null)
const cityOrgId = computed(() => appStore.cityCode === '110000' ? 'ORG-BJ' : 'ORG-SH')

const fenceTypeLabels: Record<FenceType, string> = {
  OPERATION: '运营区域', NO_RIDE: '禁骑区域', NO_PARK: '禁停区域',
}
const violationLabels: Record<string, string> = {
  OUTSIDE_OPERATION: '超出运营区', IN_NO_PARK: '禁停区停放', RIDING_IN_NO_RIDE: '禁骑区骑行',
}

/** 输入: 当前城市; 输出: 有效设施和实时违规总览。 */
async function loadOverview() {
  loading.value = true
  error.value = ''
  try {
    overview.value = await getGeoOverview(appStore.cityCode)
  } catch (cause) {
    error.value = errorMessage(cause)
  } finally {
    loading.value = false
  }
}

/** 输入: 可选现有围栏; 输出: 打开新建或编辑抽屉。 */
function openFence(fence?: Geofence) {
  editor.value = 'fence'
  editingId.value = fence?.fenceId ?? null
  fenceForm.fenceName = fence?.fenceName ?? ''
  fenceForm.fenceType = fence?.fenceType ?? 'OPERATION'
  fenceForm.orgId = fence?.orgId ?? cityOrgId.value
  draftBoundary.value = fence ? fence.boundary.map((point) => ({ ...point })) : []
  draftLocation.value = null
  editorVisible.value = true
}

/** 输入: 可选现有停车点; 输出: 打开新建或编辑抽屉。 */
function openParking(point?: ParkingPoint) {
  editor.value = 'parking'
  editingId.value = point?.pointId ?? null
  parkingForm.pointName = point?.pointName ?? ''
  parkingForm.orgId = point?.orgId ?? cityOrgId.value
  parkingForm.radiusMeters = point?.radiusMeters ?? 300
  parkingForm.capacity = point?.capacity ?? 80
  draftLocation.value = point ? { ...point.location } : null
  draftBoundary.value = []
  editorVisible.value = true
}

/** 输入: 地图经纬度; 输出: 追加围栏顶点或更新停车位置。 */
function mapClick(coordinate: Coordinate) {
  if (editor.value === 'fence') {
    const closed = draftBoundary.value.length > 3
      && sameCoordinate(draftBoundary.value[0]!, draftBoundary.value.at(-1)!)
    if (closed) draftBoundary.value.pop()
    draftBoundary.value.push(coordinate)
  } else if (editor.value === 'parking') {
    draftLocation.value = coordinate
  }
}

/** 输入: 两个坐标; 输出: 是否为同一点。 */
function sameCoordinate(first: Coordinate, second: Coordinate): boolean {
  return first.longitude === second.longitude && first.latitude === second.latitude
}

/** 输入: 当前围栏顶点; 输出: 首尾闭合后的边界。 */
function closeBoundary() {
  if (draftBoundary.value.length < 3) {
    ElMessage.warning('至少选取 3 个顶点')
    return
  }
  if (!sameCoordinate(draftBoundary.value[0]!, draftBoundary.value.at(-1)!)) {
    draftBoundary.value.push({ ...draftBoundary.value[0]! })
  }
}

/** 输入: 无; 输出: 清空当前围栏草稿。 */
function redrawBoundary() { draftBoundary.value = [] }

/** 输入: 当前设施表单和地图草稿; 输出: 新建或更新设施。 */
async function submitEditor() {
  saving.value = true
  try {
    if (editor.value === 'fence') {
      closeBoundary()
      if (draftBoundary.value.length < 4 || !fenceForm.fenceName.trim()) return
      await saveFence({
        ...fenceForm,
        fenceName: fenceForm.fenceName.trim(),
        cityCode: appStore.cityCode,
        status: 'ACTIVE',
        boundary: draftBoundary.value,
      }, editingId.value ?? undefined)
    } else if (editor.value === 'parking') {
      if (!draftLocation.value || !parkingForm.pointName.trim()) {
        ElMessage.warning('请填写名称并在地图上选择位置')
        return
      }
      await saveParkingPoint({
        ...parkingForm,
        pointName: parkingForm.pointName.trim(),
        cityCode: appStore.cityCode,
        status: 'ACTIVE',
        location: draftLocation.value,
      }, editingId.value ?? undefined)
    }
    ElMessage.success(editingId.value ? '设施已更新' : '设施已创建')
    editorVisible.value = false
    await loadOverview()
  } catch (cause) {
    ElMessage.error(errorMessage(cause))
  } finally {
    saving.value = false
  }
}

/** 输入: 设施类型、编号和名称; 输出: 确认后停用设施。 */
async function removeFacility(type: 'fence' | 'parking', id: string, name: string) {
  try {
    await ElMessageBox.confirm(`停用“${name}”后将不再参与空间判定，是否继续？`, '停用设施', { type: 'warning' })
    if (type === 'fence') await disableFence(id)
    else await disableParkingPoint(id)
    ElMessage.success('设施已停用')
    await loadOverview()
  } catch (cause) {
    if (cause === 'cancel' || cause === 'close') return
    ElMessage.error(errorMessage(cause))
  }
}

watch(() => appStore.cityCode, () => { editorVisible.value = false; void loadOverview() })
onMounted(loadOverview)
</script>

<template>
  <div class="page-view geo-page">
    <div class="geo-heading">
      <div class="page-heading">
        <div><h1>围栏与停车点</h1><p>维护空间规则并查看实时违规车辆</p></div>
        <div class="geo-actions">
          <el-radio-group v-model="appStore.cityCode">
            <el-radio-button v-for="item in CITIES" :key="item.code" :value="item.code">{{ item.name }}</el-radio-button>
          </el-radio-group>
          <el-button type="primary" :icon="Plus" @click="openFence()">新建围栏</el-button>
          <el-button :icon="Plus" @click="openParking()">新建停车点</el-button>
          <el-button :icon="Refresh" circle aria-label="刷新" :loading="loading" @click="loadOverview" />
        </div>
      </div>
    </div>

    <div class="geo-workspace">
      <GeoFacilityMap
        :city="city"
        :fences="overview.fences"
        :parking-points="overview.parkingPoints"
        :violations="overview.violations"
        :draft-boundary="draftBoundary"
        :draft-location="draftLocation"
        :drawing="drawing"
        @map-click="mapClick"
      />

      <aside class="geo-side">
        <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
        <el-tabs v-model="activeTab" stretch>
          <el-tab-pane :label="`空间设施 ${overview.fences.length + overview.parkingPoints.length}`" name="facilities">
            <div class="facility-list">
              <article v-for="fence in overview.fences" :key="fence.fenceId">
                <div class="facility-icon fence">围</div>
                <div><strong>{{ fence.fenceName }}</strong><span>{{ fenceTypeLabels[fence.fenceType] }} · {{ Math.round(fence.areaSquareMeters / 10000) }} 公顷</span></div>
                <div class="facility-tools">
                  <el-button :icon="Edit" text circle aria-label="编辑围栏" @click="openFence(fence)" />
                  <el-button :icon="Delete" text circle aria-label="停用围栏" @click="removeFacility('fence', fence.fenceId, fence.fenceName)" />
                </div>
              </article>
              <article v-for="point in overview.parkingPoints" :key="point.pointId">
                <div class="facility-icon parking">停</div>
                <div><strong>{{ point.pointName }}</strong><span>{{ point.vehicleCount }} 辆 / 容量 {{ point.capacity }} · 半径 {{ point.radiusMeters }}m</span></div>
                <div class="facility-tools">
                  <el-button :icon="Edit" text circle aria-label="编辑停车点" @click="openParking(point)" />
                  <el-button :icon="Delete" text circle aria-label="停用停车点" @click="removeFacility('parking', point.pointId, point.pointName)" />
                </div>
              </article>
              <el-empty v-if="!overview.fences.length && !overview.parkingPoints.length" description="暂无空间设施" :image-size="60" />
            </div>
          </el-tab-pane>
          <el-tab-pane :label="`实时违规 ${overview.violations.length}`" name="violations">
            <div class="violation-list">
              <article v-for="item in overview.violations" :key="`${item.vehicleId}-${item.violationType}`">
                <i />
                <div><strong>{{ item.vehicleId }}</strong><span>{{ violationLabels[item.violationType] ?? item.violationType }}</span><small>{{ item.facilityName ?? '运营区边界' }} · {{ formatTime(item.reportedAt) }}</small></div>
                <b>{{ item.batteryPercent ?? '--' }}%</b>
              </article>
              <el-empty v-if="!overview.violations.length" description="当前没有空间违规" :image-size="60" />
            </div>
          </el-tab-pane>
        </el-tabs>
      </aside>
    </div>

    <el-drawer v-model="editorVisible" :title="`${editingId ? '编辑' : '新建'}${editor === 'fence' ? '围栏' : '停车点'}`" size="380px" destroy-on-close>
      <div v-if="editor === 'fence'" class="editor-form">
        <el-form label-position="top">
          <el-form-item label="围栏名称"><el-input v-model="fenceForm.fenceName" maxlength="64" /></el-form-item>
          <el-form-item label="围栏类型">
            <el-select v-model="fenceForm.fenceType" style="width: 100%">
              <el-option v-for="(label, value) in fenceTypeLabels" :key="value" :label="label" :value="value" />
            </el-select>
          </el-form-item>
          <el-form-item label="地图顶点">
            <div class="draw-tools">
              <span>{{ Math.max(draftBoundary.length - (draftBoundary.length > 3 && sameCoordinate(draftBoundary[0]!, draftBoundary.at(-1)!) ? 1 : 0), 0) }} 个顶点</span>
              <el-tooltip content="撤销最后一个顶点"><el-button :icon="RefreshLeft" circle :disabled="!draftBoundary.length" @click="draftBoundary.pop()" /></el-tooltip>
              <el-tooltip content="闭合围栏"><el-button :icon="Check" circle @click="closeBoundary" /></el-tooltip>
              <el-tooltip content="重新绘制"><el-button :icon="Remove" circle @click="redrawBoundary" /></el-tooltip>
            </div>
          </el-form-item>
        </el-form>
      </div>
      <div v-else class="editor-form">
        <el-form label-position="top">
          <el-form-item label="停车点名称"><el-input v-model="parkingForm.pointName" maxlength="64" /></el-form-item>
          <el-form-item label="停车半径"><el-input-number v-model="parkingForm.radiusMeters" :min="10" :max="2000" :step="10" /><span class="unit">米</span></el-form-item>
          <el-form-item label="规划容量"><el-input-number v-model="parkingForm.capacity" :min="1" :max="10000" /><span class="unit">辆</span></el-form-item>
          <el-form-item label="地图位置">
            <span v-if="draftLocation" class="coordinate-value">{{ draftLocation.longitude.toFixed(6) }}, {{ draftLocation.latitude.toFixed(6) }}</span>
            <span v-else class="muted-text">请点击地图选择停车点</span>
          </el-form-item>
        </el-form>
      </div>
      <template #footer><el-button @click="editorVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="submitEditor">保存</el-button></template>
    </el-drawer>
  </div>
</template>

<style scoped>
.geo-page { display: grid; grid-template-rows: 78px minmax(0, 1fr); }
.geo-heading { display: flex; align-items: center; padding: 0 18px; background: #fff; border-bottom: 1px solid var(--line); }
.geo-heading .page-heading { width: 100%; }.geo-actions { display: flex; align-items: center; gap: 9px; }
.geo-workspace { display: grid; grid-template-columns: minmax(0, 1fr) 390px; min-height: 0; }
.geo-side { min-height: 0; padding: 10px 14px; overflow: auto; background: #fff; border-left: 1px solid var(--line); }
.facility-list, .violation-list { display: grid; gap: 2px; }
.facility-list article { display: grid; grid-template-columns: 38px minmax(0, 1fr) 66px; align-items: center; min-height: 66px; border-bottom: 1px solid #e9edeb; }
.facility-icon { display: grid; place-items: center; width: 30px; height: 30px; color: #fff; border-radius: 4px; font-size: 12px; font-weight: 700; }.facility-icon.fence { background: #287c5d; }.facility-icon.parking { background: #2e76a5; }
.facility-list strong, .facility-list span { display: block; }.facility-list strong { font-size: 13px; }.facility-list span { margin-top: 4px; color: var(--muted); font-size: 11px; }.facility-tools { display: flex; }
.violation-list article { display: grid; grid-template-columns: 16px minmax(0, 1fr) auto; align-items: center; min-height: 70px; border-bottom: 1px solid #e9edeb; }.violation-list i { width: 8px; height: 8px; background: #ce4646; border-radius: 50%; }.violation-list strong, .violation-list span, .violation-list small { display: block; }.violation-list strong { font-size: 13px; }.violation-list span { margin-top: 3px; color: #b53d3d; font-size: 11px; }.violation-list small { margin-top: 3px; color: var(--muted); }.violation-list b { font-size: 12px; }
.editor-form { padding: 0 4px; }.draw-tools { display: flex; align-items: center; width: 100%; gap: 7px; }.draw-tools span { flex: 1; color: var(--muted); font-size: 12px; }.unit { margin-left: 8px; color: var(--muted); }.coordinate-value { font-family: Consolas, monospace; font-size: 12px; }
</style>

<script setup lang="ts">
import { Download, Edit, Plus, Refresh, UploadFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'

import { getOrganizations } from '@/api/admin'
import { getAdminCities, saveCity } from '@/api/city'
import { createVehicle, createVehiclesBatch } from '@/api/fleet'
import { errorMessage } from '@/api/http'
import { useAppStore } from '@/stores/app'
import type { Organization } from '@/types/operations'
import type {
  AdminCity, CityRequest, LifecycleStatus, VehicleBatchResult, VehicleCreateRequest,
} from '@/types/vehicle'
import { lifecycleLabels } from '@/utils/vehicle'
import { parseVehicleCsv, VEHICLE_CSV_HEADERS } from '@/utils/fleet'

const appStore = useAppStore()
const activeTab = ref('cities')
const loading = ref(false)
const saving = ref(false)
const cities = ref<AdminCity[]>([])
const organizations = ref<Organization[]>([])
const cityDialogVisible = ref(false)
const editingCityCode = ref<string | null>(null)
const csvText = ref('')
const batchResult = ref<VehicleBatchResult | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)

const cityForm = reactive<CityRequest>({
  cityCode: '', cityName: '', orgId: '', centerLongitude: 0, centerLatitude: 0,
  minLongitude: 0, minLatitude: 0, maxLongitude: 0, maxLatitude: 0, status: 'ACTIVE',
})
const vehicleForm = reactive<VehicleCreateRequest>({
  vehicleId: '', companyId: 'DEMO', lockId: '', controllerId: '', plateNumber: null,
  filingCode: null, model: '', batchNo: null, operationCityCode: appStore.cityCode,
  operationAreaCode: appStore.cityCode, launchDate: localToday(), lifecycleStatus: 'PENDING',
})
const batchPreview = computed(() => parseVehicleCsv(csvText.value, new Set(appStore.cities.map((city) => city.code))))
const availableOrganizations = computed(() => organizations.value.filter((org) =>
  org.status === 'ACTIVE' && org.cityCode === cityForm.cityCode,
))

/** 输入: 本机当前日期; 输出: YYYY-MM-DD。 */
function localToday(): string {
  const now = new Date()
  const offset = now.getTimezoneOffset() * 60_000
  return new Date(now.getTime() - offset).toISOString().slice(0, 10)
}

/** 输入: 当前管理员会话; 输出: 城市配置和可绑定组织。 */
async function loadData(): Promise<void> {
  loading.value = true
  try {
    const [cityItems, organizationItems] = await Promise.all([getAdminCities(), getOrganizations()])
    cities.value = cityItems
    organizations.value = organizationItems
  } catch (cause) {
    ElMessage.error(errorMessage(cause))
  } finally {
    loading.value = false
  }
}

/** 输入: 可选现有城市; 输出: 打开新增或编辑表单。 */
function openCity(city?: AdminCity): void {
  editingCityCode.value = city?.code ?? null
  Object.assign(cityForm, city ? {
    cityCode: city.code, cityName: city.name, orgId: city.orgId,
    centerLongitude: city.center[0], centerLatitude: city.center[1],
    minLongitude: city.bounds[0], minLatitude: city.bounds[1],
    maxLongitude: city.bounds[2], maxLatitude: city.bounds[3], status: city.status,
  } : {
    cityCode: '', cityName: '', orgId: '', centerLongitude: 0, centerLatitude: 0,
    minLongitude: 0, minLatitude: 0, maxLongitude: 0, maxLatitude: 0, status: 'ACTIVE',
  })
  cityDialogVisible.value = true
}

/** 输入: 城市配置; 输出: 保存配置并刷新所有页面可用城市。 */
async function submitCity(): Promise<void> {
  if (!cityForm.cityName.trim() || !cityForm.orgId) {
    ElMessage.warning('请填写城市名称并选择负责组织')
    return
  }
  saving.value = true
  try {
    await saveCity({ ...cityForm, cityName: cityForm.cityName.trim() }, editingCityCode.value ?? undefined)
    await Promise.all([loadData(), appStore.ensureCities(true)])
    cityDialogVisible.value = false
    ElMessage.success(editingCityCode.value ? '城市配置已更新' : '城市已启用')
  } catch (cause) {
    ElMessage.error(errorMessage(cause))
  } finally {
    saving.value = false
  }
}

/** 输入: 单辆车辆表单; 输出: 新增车辆并清空唯一编号字段。 */
async function submitVehicle(): Promise<void> {
  if (!vehicleForm.vehicleId || !vehicleForm.lockId || !vehicleForm.controllerId || !vehicleForm.model) {
    ElMessage.warning('请填写车辆、车锁、控制器编号和型号')
    return
  }
  saving.value = true
  try {
    const vehicleId = await createVehicle(vehicleForm)
    ElMessage.success(`车辆 ${vehicleId} 已创建`)
    vehicleForm.vehicleId = ''
    vehicleForm.lockId = ''
    vehicleForm.controllerId = ''
    vehicleForm.plateNumber = null
    vehicleForm.filingCode = null
  } catch (cause) {
    ElMessage.error(errorMessage(cause))
  } finally {
    saving.value = false
  }
}

/** 输入: CSV 文本; 输出: 提交预检通过的车辆并展示服务端逐行结果。 */
async function submitBatch(): Promise<void> {
  if (batchPreview.value.errors.length || batchPreview.value.rows.length === 0) {
    ElMessage.warning('请先修正 CSV 预检错误')
    return
  }
  saving.value = true
  try {
    batchResult.value = await createVehiclesBatch(batchPreview.value.rows)
    ElMessage.success(`成功创建 ${batchResult.value.createdCount} 辆，跳过 ${batchResult.value.skippedCount} 辆`)
  } catch (cause) {
    ElMessage.error(errorMessage(cause))
  } finally {
    saving.value = false
  }
}

/** 输入: 用户选择的 UTF-8 CSV 文件; 输出: 读取文本并触发实时预检。 */
async function readCsvFile(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (file.size > 1_000_000) {
    ElMessage.error('CSV 文件不能超过 1 MB')
    return
  }
  csvText.value = await file.text()
  batchResult.value = null
  input.value = ''
}

/** 输入: 无; 输出: 下载包含表头和示例行的 UTF-8 CSV 模板。 */
function downloadTemplate(): void {
  const city = appStore.currentCity
  const example = [
    'BIKE-NEW-001', 'DEMO', 'LOCK-NEW-001', 'CTRL-NEW-001', '', '', 'YD-DEMO',
    'BATCH-001', city.code, city.code, localToday(), 'PENDING',
  ]
  const blob = new Blob([`\uFEFF${VEHICLE_CSV_HEADERS.join(',')}\n${example.join(',')}\n`], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = '车辆批量导入模板.csv'
  link.click()
  URL.revokeObjectURL(url)
}

onMounted(loadData)
</script>

<template>
  <div class="page-view fleet-page">
    <div class="fleet-heading">
      <div class="page-heading">
        <div><h1>城市与车辆扩展</h1><p>按组织扩展运营城市，并安全导入车辆资产</p></div>
        <el-button :icon="Refresh" circle aria-label="刷新" :loading="loading" @click="loadData" />
      </div>
    </div>

    <div class="fleet-body">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="运营城市" name="cities">
          <div class="tab-toolbar">
            <el-alert type="info" :closable="false" title="新增城市前，请先在“组织与审计”中创建同代码的启用组织。" show-icon />
            <el-button type="primary" :icon="Plus" @click="openCity()">新增城市</el-button>
          </div>
          <el-table v-loading="loading" :data="cities" stripe>
            <el-table-column prop="name" label="城市" min-width="120" />
            <el-table-column prop="code" label="行政区代码" width="120" />
            <el-table-column prop="orgName" label="负责组织" min-width="180" />
            <el-table-column label="中心点" min-width="180"><template #default="scope">{{ scope.row.center.join(', ') }}</template></el-table-column>
            <el-table-column label="地图边界" min-width="250"><template #default="scope">{{ scope.row.bounds.join(', ') }}</template></el-table-column>
            <el-table-column label="状态" width="90"><template #default="scope"><el-tag :type="scope.row.status === 'ACTIVE' ? 'success' : 'info'">{{ scope.row.status === 'ACTIVE' ? '启用' : '停用' }}</el-tag></template></el-table-column>
            <el-table-column label="操作" width="80"><template #default="scope"><el-button text circle :icon="Edit" aria-label="编辑城市" @click="openCity(scope.row)" /></template></el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="单辆添加" name="single">
          <el-form class="vehicle-form" label-position="top">
            <div class="form-grid three"><el-form-item label="车辆编号"><el-input v-model="vehicleForm.vehicleId" /></el-form-item><el-form-item label="车锁编号"><el-input v-model="vehicleForm.lockId" /></el-form-item><el-form-item label="控制器编号"><el-input v-model="vehicleForm.controllerId" /></el-form-item></div>
            <div class="form-grid three"><el-form-item label="所属公司"><el-input v-model="vehicleForm.companyId" /></el-form-item><el-form-item label="车辆型号"><el-input v-model="vehicleForm.model" /></el-form-item><el-form-item label="采购批次"><el-input v-model="vehicleForm.batchNo" /></el-form-item></div>
            <div class="form-grid three"><el-form-item label="运营城市"><el-select v-model="vehicleForm.operationCityCode" style="width:100%" @change="vehicleForm.operationAreaCode = vehicleForm.operationCityCode"><el-option v-for="city in appStore.cities" :key="city.code" :label="city.name" :value="city.code" /></el-select></el-form-item><el-form-item label="运营区域代码"><el-input v-model="vehicleForm.operationAreaCode" maxlength="6" /></el-form-item><el-form-item label="投放日期"><el-date-picker v-model="vehicleForm.launchDate" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></div>
            <div class="form-grid three"><el-form-item label="车牌号"><el-input v-model="vehicleForm.plateNumber" /></el-form-item><el-form-item label="备案编号"><el-input v-model="vehicleForm.filingCode" /></el-form-item><el-form-item label="生命周期"><el-select v-model="vehicleForm.lifecycleStatus" style="width:100%"><el-option v-for="(label, value) in lifecycleLabels" :key="value" :label="label" :value="value as LifecycleStatus" /></el-select></el-form-item></div>
            <el-button type="primary" :icon="Plus" :loading="saving" @click="submitVehicle">添加车辆</el-button>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="CSV 批量导入" name="batch">
          <div class="batch-toolbar">
            <input ref="fileInput" class="file-input" type="file" accept=".csv,text/csv" @change="readCsvFile" />
            <el-button :icon="UploadFilled" @click="fileInput?.click()">选择 CSV</el-button>
            <el-button :icon="Download" @click="downloadTemplate">下载模板</el-button>
            <span>UTF-8 编码，单次最多 500 辆；支持部分成功，不覆盖已有车辆。</span>
          </div>
          <el-input v-model="csvText" type="textarea" :rows="9" placeholder="粘贴 CSV，首行必须使用模板表头" />
          <div class="preview-summary">
            <el-tag type="success">预检通过 {{ batchPreview.rows.length }} 行</el-tag>
            <el-tag v-if="batchPreview.errors.length" type="danger">错误 {{ batchPreview.errors.length }} 行</el-tag>
            <el-button type="primary" :loading="saving" :disabled="batchPreview.rows.length === 0 || batchPreview.errors.length > 0" @click="submitBatch">确认导入</el-button>
          </div>
          <el-alert v-for="item in batchPreview.errors.slice(0, 10)" :key="item" type="error" :title="item" :closable="false" />
          <el-table v-if="batchPreview.rows.length" :data="batchPreview.rows.slice(0, 20)" size="small" stripe>
            <el-table-column prop="vehicleId" label="车辆编号" /><el-table-column prop="model" label="型号" /><el-table-column prop="operationCityCode" label="城市" /><el-table-column prop="operationAreaCode" label="区域" /><el-table-column prop="launchDate" label="投放日期" />
          </el-table>
          <el-alert v-if="batchResult" class="result-alert" :type="batchResult.skippedCount ? 'warning' : 'success'" :title="`服务端结果：创建 ${batchResult.createdCount}，跳过 ${batchResult.skippedCount}`" :closable="false" />
          <el-table v-if="batchResult?.skipped.length" :data="batchResult.skipped" size="small"><el-table-column prop="rowNumber" label="数据行" width="90" /><el-table-column prop="vehicleId" label="车辆编号" /><el-table-column prop="reason" label="跳过原因" min-width="260" /></el-table>
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-dialog v-model="cityDialogVisible" :title="editingCityCode ? '编辑城市' : '新增城市'" width="620px">
      <el-form label-position="top">
        <div class="form-grid two"><el-form-item label="行政区代码"><el-input v-model="cityForm.cityCode" maxlength="6" :disabled="Boolean(editingCityCode)" /></el-form-item><el-form-item label="城市名称"><el-input v-model="cityForm.cityName" /></el-form-item></div>
        <el-form-item label="负责组织"><el-select v-model="cityForm.orgId" filterable style="width:100%" placeholder="选择同城市代码的启用组织"><el-option v-for="org in availableOrganizations" :key="org.orgId" :label="org.orgName" :value="org.orgId" /></el-select></el-form-item>
        <div class="form-grid two"><el-form-item label="中心经度"><el-input-number v-model="cityForm.centerLongitude" :precision="6" :min="-180" :max="180" controls-position="right" /></el-form-item><el-form-item label="中心纬度"><el-input-number v-model="cityForm.centerLatitude" :precision="6" :min="-90" :max="90" controls-position="right" /></el-form-item></div>
        <div class="form-grid two"><el-form-item label="最小经度"><el-input-number v-model="cityForm.minLongitude" :precision="6" :min="-180" :max="180" /></el-form-item><el-form-item label="最小纬度"><el-input-number v-model="cityForm.minLatitude" :precision="6" :min="-90" :max="90" /></el-form-item><el-form-item label="最大经度"><el-input-number v-model="cityForm.maxLongitude" :precision="6" :min="-180" :max="180" /></el-form-item><el-form-item label="最大纬度"><el-input-number v-model="cityForm.maxLatitude" :precision="6" :min="-90" :max="90" /></el-form-item></div>
        <el-form-item label="状态"><el-switch v-model="cityForm.status" active-value="ACTIVE" inactive-value="DISABLED" active-text="启用" inactive-text="停用" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="cityDialogVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="submitCity">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.fleet-page { display:grid; grid-template-rows:78px minmax(0,1fr); background:#fff; }
.fleet-heading { display:flex; align-items:center; padding:0 18px; border-bottom:1px solid var(--line); }
.fleet-heading .page-heading { width:100%; }
.fleet-body { min-height:0; padding:0 18px 20px; overflow:auto; }
.tab-toolbar,.batch-toolbar,.preview-summary { display:flex; align-items:center; gap:10px; min-height:58px; }
.tab-toolbar .el-alert { flex:1; }
.batch-toolbar span { color:var(--muted); font-size:12px; }
.file-input { display:none; }
.vehicle-form { max-width:980px; padding-top:18px; }
.form-grid { display:grid; gap:14px; }.form-grid.two { grid-template-columns:1fr 1fr; }.form-grid.three { grid-template-columns:repeat(3,1fr); }
.form-grid :deep(.el-input-number) { width:100%; }
.preview-summary { justify-content:flex-start; }.preview-summary .el-button { margin-left:auto; }
.fleet-body > :deep(.el-tabs) { min-height:100%; }
.result-alert { margin:12px 0; }
.fleet-body :deep(.el-alert + .el-alert) { margin-top:6px; }
</style>

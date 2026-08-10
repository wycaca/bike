<script setup lang="ts">
import { Refresh, Search } from '@element-plus/icons-vue'
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

import { errorMessage } from '@/api/http'
import { getVehicles } from '@/api/vehicle'
import VehicleConditionTag from '@/components/VehicleConditionTag.vue'
import VehicleDetailDrawer from '@/components/VehicleDetailDrawer.vue'
import { useAppStore } from '@/stores/app'
import type { LifecycleStatus, VehicleListItem } from '@/types/vehicle'
import {
  CITIES,
  cityName,
  formatTime,
  lifecycleLabels,
  rideLabels,
} from '@/utils/vehicle'

const appStore = useAppStore()
const items = ref<VehicleListItem[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const keyword = ref('')
const lifecycleStatus = ref<LifecycleStatus | ''>('')
const loading = ref(false)
const error = ref('')
const selectedVehicleId = ref<string | null>(null)
const drawerVisible = ref(false)
let requestController: AbortController | null = null

async function loadVehicles() {
  requestController?.abort()
  requestController = new AbortController()
  loading.value = true
  error.value = ''
  try {
    const result = await getVehicles(
      {
        page: page.value,
        pageSize: pageSize.value,
        keyword: keyword.value.trim() || undefined,
        cityCode: appStore.cityCode,
        lifecycleStatus: lifecycleStatus.value || undefined,
      },
      requestController.signal,
    )
    items.value = result.items
    total.value = result.total
  } catch (cause) {
    const message = errorMessage(cause)
    if (message) error.value = message
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  void loadVehicles()
}

function resetFilters() {
  keyword.value = ''
  lifecycleStatus.value = ''
  page.value = 1
  void loadVehicles()
}

function rideStatusLabel(row: VehicleListItem) {
  return row.latestState ? rideLabels[row.latestState.rideStatus] : '--'
}

function lifecycleStatusLabel(row: VehicleListItem) {
  return lifecycleLabels[row.lifecycleStatus]
}

function selectVehicle(row: VehicleListItem) {
  selectedVehicleId.value = row.vehicleId
  drawerVisible.value = true
}

watch(
  () => appStore.cityCode,
  () => {
    page.value = 1
    void loadVehicles()
  },
)

onMounted(loadVehicles)
onBeforeUnmount(() => requestController?.abort())
</script>

<template>
  <div class="page-view vehicle-list-page">
    <div class="list-heading">
      <div class="page-heading">
        <div>
          <h1>车辆资产</h1>
          <p>检索车辆档案并查看最新运行状态</p>
        </div>
        <div class="list-total">共 {{ total }} 辆</div>
      </div>
    </div>

    <div class="toolbar-band list-toolbar">
      <el-input
        v-model="keyword"
        clearable
        placeholder="车辆编号、车牌或锁编号"
        :prefix-icon="Search"
        style="width: 260px"
        @keyup.enter="search"
        @clear="search"
      />
      <el-select v-model="appStore.cityCode" aria-label="运营城市" style="width: 112px">
        <el-option v-for="city in CITIES" :key="city.code" :label="city.name" :value="city.code" />
      </el-select>
      <el-select
        v-model="lifecycleStatus"
        clearable
        placeholder="全部生命周期"
        aria-label="生命周期"
        style="width: 140px"
        @change="search"
      >
        <el-option
          v-for="(label, value) in lifecycleLabels"
          :key="value"
          :label="label"
          :value="value"
        />
      </el-select>
      <el-button type="primary" :icon="Search" @click="search">查询</el-button>
      <el-button @click="resetFilters">重置</el-button>
      <el-button :icon="Refresh" :loading="loading" circle aria-label="刷新" @click="loadVehicles" />
    </div>

    <div v-if="error" class="list-error">
      <el-alert :title="error" type="error" show-icon :closable="false">
        <template #default>
          <el-button size="small" @click="loadVehicles">重新加载</el-button>
        </template>
      </el-alert>
    </div>

    <div class="table-region">
      <el-table
        v-loading="loading"
        :data="items"
        height="100%"
        stripe
        row-key="vehicleId"
        empty-text="没有符合条件的车辆"
        @row-click="selectVehicle"
      >
        <el-table-column prop="vehicleId" label="车辆编号" width="150" fixed />
        <el-table-column prop="plateNumber" label="车牌" width="145">
          <template #default="scope">{{ scope.row.plateNumber || '--' }}</template>
        </el-table-column>
        <el-table-column label="城市" width="86">
          <template #default="scope">{{ cityName(scope.row.operationCityCode) }}</template>
        </el-table-column>
        <el-table-column label="当前状态" width="100">
          <template #default="scope">
            <VehicleConditionTag :state="scope.row.latestState" />
          </template>
        </el-table-column>
        <el-table-column label="骑行状态" width="96">
          <template #default="scope">
            {{ rideStatusLabel(scope.row) }}
          </template>
        </el-table-column>
        <el-table-column label="电量" width="110" align="right">
          <template #default="scope">
            <el-progress
              v-if="scope.row.latestState?.batteryPercent != null"
              :percentage="scope.row.latestState.batteryPercent"
              :stroke-width="7"
              :show-text="false"
              :color="scope.row.latestState.batteryPercent <= 20 ? '#d68a17' : '#198754'"
            />
            <span class="battery-value">{{ scope.row.latestState?.batteryPercent ?? '--' }}%</span>
          </template>
        </el-table-column>
        <el-table-column label="剩余里程" width="100" align="right">
          <template #default="scope">
            {{ scope.row.latestState?.remainingRangeKm ?? '--' }} km
          </template>
        </el-table-column>
        <el-table-column label="最后上报" min-width="150">
          <template #default="scope">{{ formatTime(scope.row.latestState?.reportedAt) }}</template>
        </el-table-column>
        <el-table-column label="生命周期" width="94">
          <template #default="scope">
            {{ lifecycleStatusLabel(scope.row) }}
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="pagination-band">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        background
        layout="total, sizes, prev, pager, next"
        :total="total"
        :page-sizes="[20, 50, 100]"
        @current-change="loadVehicles"
        @size-change="search"
      />
    </div>

    <VehicleDetailDrawer v-model="drawerVisible" :vehicle-id="selectedVehicleId" />
  </div>
</template>

<style scoped>
.vehicle-list-page {
  display: grid;
  grid-template-rows: 78px 58px auto minmax(0, 1fr) 58px;
  background: #ffffff;
}

.list-heading {
  display: flex;
  align-items: center;
  padding: 0 18px;
  border-bottom: 1px solid var(--line);
}

.list-heading .page-heading {
  width: 100%;
}

.list-total {
  color: #62706a;
  font-size: 13px;
}

.list-toolbar {
  min-height: 58px;
}

.list-error {
  padding: 10px 14px 0;
}

.table-region {
  min-height: 0;
  padding: 12px 14px 0;
}

.table-region :deep(.el-table__row) {
  cursor: pointer;
}

.table-region :deep(.el-table__header th) {
  color: #4f5c57;
  background: #f1f4f2;
}

.battery-value {
  display: inline-block;
  min-width: 34px;
  margin-top: 4px;
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}

.pagination-band {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: 0 14px;
  border-top: 1px solid var(--line);
}
</style>

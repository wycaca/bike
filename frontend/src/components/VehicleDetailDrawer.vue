<script setup lang="ts">
import { Clock, Location, RefreshRight } from '@element-plus/icons-vue'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { errorMessage } from '@/api/http'
import { getVehicle } from '@/api/vehicle'
import type { VehicleDetail } from '@/types/vehicle'
import {
  cityName,
  controllerLabels,
  formatTime,
  lifecycleLabels,
  lockLabels,
  rideLabels,
} from '@/utils/vehicle'
import VehicleConditionTag from './VehicleConditionTag.vue'

const props = defineProps<{
  modelValue: boolean
  vehicleId: string | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const router = useRouter()
const detail = ref<VehicleDetail | null>(null)
const loading = ref(false)
const error = ref('')
let controller: AbortController | null = null

const title = computed(() => detail.value?.asset.vehicleId ?? props.vehicleId ?? '车辆详情')

async function loadDetail() {
  if (!props.modelValue || !props.vehicleId) return
  detail.value = null
  controller?.abort()
  controller = new AbortController()
  loading.value = true
  error.value = ''
  try {
    detail.value = await getVehicle(props.vehicleId, controller.signal)
  } catch (cause) {
    const message = errorMessage(cause)
    if (message) error.value = message
  } finally {
    loading.value = false
  }
}

function openTrajectory() {
  if (!props.vehicleId) return
  emit('update:modelValue', false)
  void router.push({ name: 'trajectory', params: { vehicleId: props.vehicleId } })
}

watch(() => [props.modelValue, props.vehicleId], loadDetail, { immediate: true })
onBeforeUnmount(() => controller?.abort())
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    size="460px"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <template #header>
      <div class="drawer-heading">
        <div>
          <strong>{{ title }}</strong>
          <span v-if="detail?.asset.plateNumber">{{ detail.asset.plateNumber }}</span>
        </div>
        <VehicleConditionTag v-if="detail" :state="detail.latestState" />
      </div>
    </template>

    <el-skeleton v-if="loading && !detail" :rows="10" animated />

    <el-alert v-else-if="error && !detail" :title="error" type="error" show-icon :closable="false">
      <template #default>
        <el-button :icon="RefreshRight" size="small" @click="loadDetail">重新加载</el-button>
      </template>
    </el-alert>

    <div v-else-if="detail" v-loading="loading" class="drawer-body">
      <section>
        <h2 class="section-title">实时状态</h2>
        <div v-if="detail.latestState" class="metric-grid">
          <div class="metric-item">
            <span>剩余电量</span>
            <strong>{{ detail.latestState.batteryPercent ?? '--' }}%</strong>
          </div>
          <div class="metric-item">
            <span>预估续航</span>
            <strong>{{ detail.latestState.remainingRangeKm ?? '--' }} km</strong>
          </div>
          <div class="metric-item">
            <span>骑行状态</span>
            <strong>{{ rideLabels[detail.latestState.rideStatus] }}</strong>
          </div>
          <div class="metric-item">
            <span>车辆锁</span>
            <strong>{{ lockLabels[detail.latestState.lockStatus] }}</strong>
          </div>
        </div>
        <el-empty v-else :image-size="64" description="暂无车辆状态" />
      </section>

      <section v-if="detail.latestState" class="detail-section">
        <h2 class="section-title">定位信息</h2>
        <el-descriptions :column="1" size="small" border>
          <el-descriptions-item label="上报时间">
            <el-icon><Clock /></el-icon>
            {{ formatTime(detail.latestState.reportedAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="当前位置">
            <el-icon><Location /></el-icon>
            <span class="numeric-text">
              {{ detail.latestState.longitude.toFixed(6) }},
              {{ detail.latestState.latitude.toFixed(6) }}
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="坐标系">
            {{ detail.latestState.coordinateSystem }}
          </el-descriptions-item>
          <el-descriptions-item label="定位精度">
            {{ detail.latestState.accuracyMeters ?? '--' }} m
          </el-descriptions-item>
          <el-descriptions-item label="控制器">
            {{ controllerLabels[detail.latestState.controllerStatus] }}
          </el-descriptions-item>
          <el-descriptions-item label="信号强度">
            {{ detail.latestState.signalStrength ?? '--' }}
          </el-descriptions-item>
        </el-descriptions>
      </section>

      <section class="detail-section">
        <h2 class="section-title">车辆档案</h2>
        <el-descriptions :column="1" size="small" border>
          <el-descriptions-item label="运营城市">
            {{ cityName(detail.asset.operationCityCode) }}
          </el-descriptions-item>
          <el-descriptions-item label="车辆型号">{{ detail.asset.model }}</el-descriptions-item>
          <el-descriptions-item label="智能锁编号">{{ detail.asset.lockId }}</el-descriptions-item>
          <el-descriptions-item label="控制器编号">
            {{ detail.asset.controllerId }}
          </el-descriptions-item>
          <el-descriptions-item label="备案编号">
            {{ detail.asset.filingCode || '--' }}
          </el-descriptions-item>
          <el-descriptions-item label="生命周期">
            {{ lifecycleLabels[detail.asset.lifecycleStatus] }}
          </el-descriptions-item>
        </el-descriptions>
      </section>

      <section v-if="detail.latestState?.faultCodes.length" class="detail-section">
        <h2 class="section-title">当前故障</h2>
        <el-tag
          v-for="fault in detail.latestState.faultCodes"
          :key="fault"
          type="danger"
          effect="plain"
        >
          {{ fault }}
        </el-tag>
      </section>
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">关闭</el-button>
      <el-button type="primary" :icon="Location" :disabled="!detail" @click="openTrajectory">
        查看轨迹
      </el-button>
    </template>
  </el-drawer>
</template>

<style scoped>
.drawer-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding-right: 12px;
}

.drawer-heading > div {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.drawer-heading strong {
  color: #17231f;
  font-size: 16px;
}

.drawer-heading span {
  color: #6a7571;
  font-size: 12px;
}

.drawer-body {
  min-height: 420px;
}

.metric-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1px;
  overflow: hidden;
  background: #dfe5e2;
  border: 1px solid #dfe5e2;
  border-radius: 4px;
}

.metric-item {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-height: 76px;
  padding: 13px 14px;
  background: #f8faf9;
}

.metric-item span {
  color: #6b7672;
  font-size: 12px;
}

.metric-item strong {
  color: #1e2d27;
  font-size: 17px;
}

.detail-section {
  margin-top: 22px;
}

.el-descriptions :deep(.el-descriptions__label) {
  width: 100px;
  color: #5f6d67;
}

.el-descriptions .el-icon {
  margin-right: 5px;
  vertical-align: -2px;
}
</style>

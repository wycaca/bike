<script setup lang="ts">
import { showToast } from 'vant'
import { onMounted, ref, watch } from 'vue'

import { errorText, getVehicles } from '@/api'
import { scanVehicleCode } from '@/bridge'
import { useAppStore } from '@/stores/app'
import type { Vehicle } from '@/types'

const app = useAppStore()
const keyword = ref('')
const vehicles = ref<Vehicle[]>([])
const loading = ref(false)

/** 输入: 城市与可选车辆编号; 输出: 符合条件的车辆快照。 */
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

/** 输入: 原生扫码结果; 输出: 自动填入车辆编号并发起查询。 */
async function scan() {
  try {
    keyword.value = await scanVehicleCode()
    await loadVehicles()
  } catch (error) {
    showToast(errorText(error))
  }
}

onMounted(loadVehicles)
watch(() => app.cityCode, loadVehicles)
</script>

<template>
  <div>
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
  </div>
</template>

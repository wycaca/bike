<script setup lang="ts">
import { showToast } from 'vant'
import { computed, onMounted, ref, watch } from 'vue'

import { errorText, getTasks, optimizeRoute } from '@/api'
import { requestLocation } from '@/bridge'
import TaskCard from '@/components/TaskCard.vue'
import { useAppStore } from '@/stores/app'
import type { RoutePlan, Task } from '@/types'
import { formatDistance } from '@/utils'

const app = useAppStore()
const tasks = ref<Task[]>([])
const selectedIds = ref<string[]>([])
const routePlan = ref<RoutePlan | null>(null)
const optimizing = ref(false)
const selectedCount = computed(() => selectedIds.value.length)

async function loadTasks() {
  try {
    const result = await getTasks({ cityCode: app.cityCode, scope: 'MINE' })
    tasks.value = result.items.filter((task) => ['CLAIMED', 'IN_PROGRESS'].includes(task.status) && task.sourceLongitude !== null)
    selectedIds.value = tasks.value.slice(0, 8).map((task) => task.taskId)
    routePlan.value = null
  } catch (error) {
    showToast(errorText(error))
  }
}

function toggle(task: Task) {
  selectedIds.value = selectedIds.value.includes(task.taskId)
    ? selectedIds.value.filter((taskId) => taskId !== task.taskId)
    : [...selectedIds.value, task.taskId]
}

/** 输入: 2 至 16 个作业点和可选当前位置; 输出: 高德道路规划后的作业顺序与里程。 */
async function optimize() {
  if (selectedCount.value < 2) return showToast('请至少选择两个任务点')
  optimizing.value = true
  try {
    let start: { longitude: number; latitude: number } | undefined
    try { start = await requestLocation() } catch { start = undefined }
    routePlan.value = await optimizeRoute(selectedIds.value, start)
    showToast('路线已优化')
  } catch (error) {
    showToast(errorText(error))
  } finally {
    optimizing.value = false
  }
}

onMounted(loadTasks)
watch(() => app.cityCode, loadTasks)
</script>

<template>
  <div>
    <template v-if="routePlan">
      <section class="route-summary"><div><strong>{{ formatDistance(routePlan.totalDistanceMeters) }}</strong><span>预计总里程</span></div><div><strong>{{ Math.ceil(routePlan.totalDurationSeconds / 60) }}</strong><span>分钟</span></div></section>
      <van-notice-bar v-if="routePlan.warning" :text="routePlan.warning" wrapable />
      <div class="content-gap">
        <article v-for="stop in routePlan.stops" :key="stop.taskId" class="route-stop">
          <b>{{ stop.sequence }}</b><div><strong>{{ stop.title }}</strong><span>{{ stop.vehicleId }}</span></div><span>{{ formatDistance(stop.legDistanceMeters) }}</span>
        </article>
      </div>
      <van-button block plain type="primary" class="content-gap" @click="routePlan = null">重新选择</van-button>
    </template>
    <template v-else>
      <div class="section-head"><h2>选择作业点</h2><span>已选 {{ selectedCount }} 项，最多 16 项</span></div>
      <div class="task-list">
        <TaskCard v-for="task in tasks" :key="task.taskId" :task="task" selectable :selected="selectedIds.includes(task.taskId)" @select="toggle" />
      </div>
      <div v-if="!tasks.length" class="empty-state">暂无可规划的作业点</div>
      <div class="sticky-action"><van-button block type="primary" :loading="optimizing" data-test="route-optimize" @click="optimize">优化 {{ selectedCount }} 个作业点</van-button></div>
    </template>
  </div>
</template>

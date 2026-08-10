<script setup lang="ts">
import { showToast } from 'vant'
import { onMounted, ref, watch } from 'vue'

import { errorText, getTasks, taskAction } from '@/api'
import TaskCard from '@/components/TaskCard.vue'
import { useAppStore } from '@/stores/app'
import type { Task, TaskType } from '@/types'
import { taskTypeLabels } from '@/utils'

const app = useAppStore()
const tasks = ref<Task[]>([])
const taskType = ref<TaskType | ''>('')
const claimingId = ref('')
const typeOptions = [
  { text: '全部类型', value: '' },
  ...Object.entries(taskTypeLabels).map(([value, text]) => ({ value: value as TaskType, text })),
]

/** 输入: 当前城市与任务类型; 输出: 尚未被领取的公开任务。 */
async function loadPool() {
  try {
    const result = await getTasks({ cityCode: app.cityCode, scope: 'UNASSIGNED', type: taskType.value || undefined })
    tasks.value = result.items.filter((task) => task.status === 'OPEN')
  } catch (error) {
    showToast(errorText(error))
  }
}

/** 输入: 待领取任务; 输出: 任务归属当前运维人员并从公共池移除。 */
async function claim(task: Task) {
  claimingId.value = task.taskId
  try {
    await taskAction(task.taskId, 'claim')
    tasks.value = tasks.value.filter((item) => item.taskId !== task.taskId)
    showToast('抢单成功，可前往“作业”处理')
  } catch (error) {
    showToast(errorText(error))
  } finally {
    claimingId.value = ''
  }
}

onMounted(loadPool)
watch(() => app.cityCode, loadPool)
</script>

<template>
  <div>
    <div class="filter-row one-wide">
      <van-dropdown-menu><van-dropdown-item v-model="taskType" :options="typeOptions" @change="loadPool" /></van-dropdown-menu>
      <van-button plain type="primary" @click="loadPool">刷新任务池</van-button>
    </div>
    <div class="section-head"><h2>附近待领取任务</h2><span>{{ tasks.length }} 项</span></div>
    <div v-if="tasks.length" class="task-list">
      <TaskCard v-for="task in tasks" :key="task.taskId" :task="task">
        <van-button size="small" type="primary" :loading="claimingId === task.taskId" :data-test="`claim-${task.taskId}`" @click="claim(task)">抢单</van-button>
      </TaskCard>
    </div>
    <div v-else class="empty-state">当前没有可领取任务</div>
  </div>
</template>

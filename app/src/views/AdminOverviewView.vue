<script setup lang="ts">
import { showToast } from 'vant'
import { onMounted, ref, watch } from 'vue'

import { errorText, getTaskSummary, getTasks } from '@/api'
import TaskCard from '@/components/TaskCard.vue'
import { useAppStore } from '@/stores/app'
import type { Task, TaskSummary } from '@/types'

const app = useAppStore()
const loading = ref(false)
const summary = ref<TaskSummary>({
  openCount: 0, claimedCount: 0, inProgressCount: 0, pendingReviewCount: 0,
  exceptionCount: 0, overdueCount: 0, completedTodayCount: 0, myActiveCount: 0,
})
const attentionTasks = ref<Task[]>([])

/** 输入: 当前城市; 输出: 管理驾驶舱汇总以及需要优先处理的任务。 */
async function loadOverview() {
  loading.value = true
  try {
    const [summaryResult, exceptionResult, reviewResult] = await Promise.all([
      getTaskSummary(app.cityCode),
      getTasks({ cityCode: app.cityCode, scope: 'ALL', status: 'EXCEPTION' }),
      getTasks({ cityCode: app.cityCode, scope: 'ALL', status: 'PENDING_REVIEW' }),
    ])
    summary.value = summaryResult
    attentionTasks.value = [...exceptionResult.items, ...reviewResult.items].slice(0, 6)
  } catch (error) {
    showToast(errorText(error))
  } finally {
    loading.value = false
  }
}

onMounted(loadOverview)
watch(() => app.cityCode, loadOverview)
</script>

<template>
  <div>
    <div class="section-head"><h2>今日运营</h2><span>{{ app.cityName }}实时任务</span></div>
    <section class="metric-grid">
      <div class="metric"><span>待领取</span><strong>{{ summary.openCount }}</strong></div>
      <div class="metric info"><span>执行中</span><strong>{{ summary.inProgressCount }}</strong></div>
      <div class="metric warning"><span>待验收</span><strong>{{ summary.pendingReviewCount }}</strong></div>
      <div class="metric danger"><span>异常任务</span><strong>{{ summary.exceptionCount }}</strong></div>
      <div class="metric warning"><span>已逾期</span><strong>{{ summary.overdueCount }}</strong></div>
      <div class="metric"><span>今日完成</span><strong>{{ summary.completedTodayCount }}</strong></div>
    </section>

    <div class="section-head content-gap"><h2>待处理事项</h2><span>{{ attentionTasks.length }} 项</span></div>
    <div v-if="attentionTasks.length" class="task-list">
      <TaskCard v-for="task in attentionTasks" :key="task.taskId" :task="task" />
    </div>
    <div v-else class="empty-state">当前没有待验收或异常任务</div>
  </div>
</template>

<script setup lang="ts">
import type { Task } from '@/types'
import { formatTime, priorityLabels, taskStatusLabels, taskTypeLabels } from '@/utils'

defineProps<{ task: Task; selectable?: boolean; selected?: boolean }>()
defineEmits<{ select: [task: Task] }>()

function statusType(status: Task['status']) {
  if (status === 'EXCEPTION') return 'danger'
  if (status === 'COMPLETED') return 'success'
  if (status === 'PENDING_REVIEW' || status === 'CLAIMED') return 'warning'
  return 'primary'
}
</script>

<template>
  <article class="task-card" :class="{ selected }" data-test="task-card" @click="$emit('select', task)">
    <div class="task-card-head">
      <div class="task-title"><strong>{{ task.title }}</strong><span>{{ task.taskNo }}</span></div>
      <van-checkbox v-if="selectable" :model-value="selected" shape="square" @click.stop="$emit('select', task)" />
      <van-tag v-else :type="statusType(task.status)" plain>{{ taskStatusLabels[task.status] }}</van-tag>
    </div>
    <div class="task-meta"><span>{{ taskTypeLabels[task.taskType] }}</span><span>{{ priorityLabels[task.priority] }}</span><span>{{ task.vehicleId }}</span></div>
    <div class="task-foot"><span>电量 {{ task.batteryPercent === null ? '--' : `${task.batteryPercent}%` }}</span><span>{{ task.assigneeName ?? '尚未领取' }}</span><span>{{ formatTime(task.dueAt) }}</span></div>
    <div class="task-actions" @click.stop><slot /></div>
  </article>
</template>

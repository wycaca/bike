<script setup lang="ts">
import type { OperationsTaskDetail, OperationsTaskPriority, OperationsTaskStatus } from '@/types/operations'
import {
  auditTime, exceptionTypeLabels, taskEventLabels, taskPriorityLabels, taskStatusLabels, taskTypeLabels,
} from '@/utils/operations'

defineProps<{ visible: boolean; detail: OperationsTaskDetail | null }>()
const emit = defineEmits<{ 'update:visible': [visible: boolean] }>()
const sourceLabels = { MANUAL: '人工', RULE: '规则', BATCH: '批量' } as const

/** 输入: 状态或优先级; 输出: Element Plus 标签色。 */
function statusTag(status: OperationsTaskStatus) {
  return ({ OPEN: 'info', CLAIMED: 'warning', IN_PROGRESS: 'primary', PENDING_REVIEW: 'warning',
    EXCEPTION: 'danger', COMPLETED: 'success', CANCELLED: 'info' } as const)[status]
}

function priorityTag(priority: OperationsTaskPriority) {
  return ({ LOW: 'info', NORMAL: 'info', HIGH: 'warning', URGENT: 'danger' } as const)[priority]
}
</script>

<template>
  <el-drawer :model-value="visible" title="任务详情" size="520px" @update:model-value="emit('update:visible', $event)">
    <template v-if="detail">
      <div class="detail-head"><div><el-tag :type="priorityTag(detail.task.priority)" size="small">{{ taskPriorityLabels[detail.task.priority] }}</el-tag><h2>{{ detail.task.title }}</h2><p>{{ detail.task.taskNo }} · {{ sourceLabels[detail.task.sourceType] }}</p></div><el-tag :type="statusTag(detail.task.status)" effect="plain">{{ taskStatusLabels[detail.task.status] }}</el-tag></div>
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="类型">{{ taskTypeLabels[detail.task.taskType] }}</el-descriptions-item><el-descriptions-item label="车辆">{{ detail.task.vehicleId }}</el-descriptions-item>
        <el-descriptions-item label="领取人">{{ detail.task.assigneeName ?? '尚未领取' }}</el-descriptions-item><el-descriptions-item label="组织">{{ detail.task.orgName }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.task.ruleName" label="触发规则" :span="2">{{ detail.task.ruleName }} · 聚合 {{ detail.task.duplicateCount }} 次</el-descriptions-item>
        <el-descriptions-item label="要求完成" :span="2">{{ auditTime(detail.task.dueAt) }}</el-descriptions-item><el-descriptions-item label="任务说明" :span="2">{{ detail.task.description ?? '--' }}</el-descriptions-item>
      </el-descriptions>

      <template v-if="detail.triggers.length"><h3 class="section-title">规则触发</h3><div v-for="trigger in detail.triggers" :key="trigger.triggerId" class="record-row"><el-tag :type="trigger.active ? 'danger' : 'success'" size="small">{{ trigger.active ? '触发中' : '已恢复' }}</el-tag><div><strong>{{ trigger.ruleName }}</strong><p>累计 {{ trigger.occurrenceCount }} 次 · 最近 {{ auditTime(trigger.lastTriggeredAt) }}</p></div></div></template>
      <template v-if="detail.evidence.length"><h3 class="section-title">作业凭证</h3><div v-for="item in detail.evidence" :key="item.evidenceId" class="evidence-record"><div class="record-title"><strong>第 {{ item.submissionNo }} 次提交</strong><el-tag :type="item.reviewStatus === 'APPROVED' ? 'success' : item.reviewStatus === 'REJECTED' ? 'danger' : 'warning'" size="small">{{ item.reviewStatus === 'APPROVED' ? '已通过' : item.reviewStatus === 'REJECTED' ? '已退回' : '待验收' }}</el-tag></div><p>{{ item.resultNote }}</p><p>{{ item.submittedByName }} · {{ auditTime(item.submittedAt) }}</p><div class="attachment-links"><a v-for="file in item.attachments" :key="file.attachmentId" :href="file.downloadUrl" target="_blank">{{ file.purpose === 'BEFORE' ? '处理前' : '处理后' }}：{{ file.originalName }}</a></div></div></template>
      <template v-if="detail.exceptions.length"><h3 class="section-title">异常闭环</h3><div v-for="item in detail.exceptions" :key="item.exceptionId" class="evidence-record exception-record"><div class="record-title"><strong>{{ exceptionTypeLabels[item.exceptionType] }}</strong><el-tag :type="item.resolvedAt ? 'success' : 'danger'" size="small">{{ item.resolvedAt ? '已处理' : '待处理' }}</el-tag></div><p>{{ item.note }}</p><p>{{ item.reportedByName }} · {{ auditTime(item.reportedAt) }}</p><p v-if="item.resolutionNote">处理结论：{{ item.resolutionNote }}</p><div class="attachment-links"><a v-for="file in item.attachments" :key="file.attachmentId" :href="file.downloadUrl" target="_blank">{{ file.originalName }}</a></div></div></template>

      <h3 class="section-title">操作时间线</h3><el-timeline><el-timeline-item v-for="event in detail.events" :key="event.eventId" :timestamp="auditTime(event.createdAt)" placement="top"><strong>{{ taskEventLabels[event.eventType] }}</strong><p>{{ event.actorName }}<template v-if="event.note"> · {{ event.note }}</template></p></el-timeline-item></el-timeline>
    </template>
  </el-drawer>
</template>

<style scoped>
.detail-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-bottom: 18px; } .detail-head h2 { margin: 8px 0 4px; font-size: 18px; letter-spacing: 0; }
.detail-head p, .el-timeline-item p, .evidence-record p, .record-row p { margin: 0; color: var(--muted); font-size: 11px; }
.section-title { margin: 22px 0 12px; font-size: 14px; letter-spacing: 0; } .record-row { display: flex; align-items: flex-start; gap: 10px; padding: 10px 0; border-bottom: 1px solid #edf0ef; }
.evidence-record { padding: 11px 12px; border-left: 3px solid #27805f; background: #f6f8f7; margin-bottom: 9px; } .exception-record { border-left-color: #c8463c; }
.record-title { display: flex; align-items: center; justify-content: space-between; margin-bottom: 6px; } .attachment-links { display: flex; flex-direction: column; gap: 3px; margin-top: 6px; }
.attachment-links a { color: #1769a8; font-size: 12px; text-decoration: none; }
</style>

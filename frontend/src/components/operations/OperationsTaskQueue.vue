<script setup lang="ts">
import { Check, CircleCheck, Location, MoreFilled, User, View } from '@element-plus/icons-vue'
import { computed } from 'vue'

import type {
  OperationsTask, OperationsTaskPriority, OperationsTaskScope, OperationsTaskStatus,
  OperationsTaskSummary, OperationsTaskType, PagedData, UserRole,
} from '@/types/operations'
import {
  auditTime, canOperateTask, isTaskOverdue, taskPriorityLabels, taskStatusLabels, taskTypeLabels,
} from '@/utils/operations'

interface TaskFilters {
  scope: OperationsTaskScope
  status: '' | OperationsTaskStatus
  type: '' | OperationsTaskType
  keyword: string
  page: number
  pageSize: number
}

const props = defineProps<{
  summary: OperationsTaskSummary
  pageData: PagedData<OperationsTask>
  filters: TaskFilters
  loading: boolean
  selectedCount: number
  actingTaskId: string
  currentRole: UserRole
  currentUserId: string
}>()

const emit = defineEmits<{
  'update-filters': [patch: Partial<TaskFilters>]
  'selection-change': [tasks: OperationsTask[]]
  load: []
  optimize: []
  claim: [task: OperationsTask]
  start: [task: OperationsTask]
  complete: [task: OperationsTask]
  'review-approve': [task: OperationsTask]
  'resolve-reopen': [task: OperationsTask]
  assign: [task: OperationsTask]
  release: [task: OperationsTask]
  exception: [task: OperationsTask]
  'review-reject': [task: OperationsTask]
  'resolve-close': [task: OperationsTask]
  cancel: [task: OperationsTask]
  detail: [taskId: string]
}>()

const summaryItems = computed(() => [
  { label: '待领取', value: props.summary.openCount, tone: 'open' },
  { label: '已领取', value: props.summary.claimedCount, tone: 'claimed' },
  { label: '执行中', value: props.summary.inProgressCount, tone: 'progress' },
  { label: '待验收', value: props.summary.pendingReviewCount, tone: 'review' },
  { label: '异常', value: props.summary.exceptionCount, tone: 'exception' },
  { label: '已超时', value: props.summary.overdueCount, tone: 'overdue' },
  { label: '今日完成', value: props.summary.completedTodayCount, tone: 'done' },
  { label: '我的任务', value: props.summary.myActiveCount, tone: 'mine' },
])
const sourceLabels = { MANUAL: '人工', RULE: '规则', BATCH: '批量' } as const

/** 输入: 局部筛选条件; 输出: 更新父页面筛选条件并重新加载第一页数据。 */
function filterAndLoad(patch: Partial<TaskFilters>) {
  emit('update-filters', { ...patch, page: 1 })
  emit('load')
}

/** 输入: 页码或每页数量; 输出: 更新分页条件并加载对应页。 */
function paginate(patch: Partial<TaskFilters>) {
  emit('update-filters', patch)
  emit('load')
}

/** 输入: 表格任务与动作; 输出: 当前角色是否允许显示该动作。 */
function can(task: OperationsTask, action: Parameters<typeof canOperateTask>[3]) {
  return canOperateTask(task, props.currentRole, props.currentUserId, action)
}

/** 输入: 任务; 输出: 是否可加入未结束任务的路线优化。 */
function routeSelectable(task: OperationsTask) {
  return ['OPEN', 'CLAIMED', 'IN_PROGRESS'].includes(task.status)
}

/** 输入: 状态或优先级; 输出: Element Plus 标签色。 */
function statusTag(status: OperationsTaskStatus) {
  return ({ OPEN: 'info', CLAIMED: 'warning', IN_PROGRESS: 'primary', PENDING_REVIEW: 'warning',
    EXCEPTION: 'danger', COMPLETED: 'success', CANCELLED: 'info' } as const)[status]
}

function priorityTag(priority: OperationsTaskPriority) {
  return ({ LOW: 'info', NORMAL: 'info', HIGH: 'warning', URGENT: 'danger' } as const)[priority]
}

function batteryText(value: number | null) { return value === null ? '--' : `${value}%` }
</script>

<template>
  <div class="task-queue">
    <section class="summary-band" aria-label="任务汇总">
      <div v-for="item in summaryItems" :key="item.label" :class="['summary-item', item.tone]">
        <span>{{ item.label }}</span><strong>{{ item.value }}</strong>
      </div>
    </section>

    <section class="task-workspace">
      <div class="task-toolbar">
        <el-radio-group :model-value="filters.scope" @update:model-value="filterAndLoad({ scope: $event as OperationsTaskScope })">
          <el-radio-button value="ALL">全部</el-radio-button>
          <el-radio-button value="UNASSIGNED">待领取</el-radio-button>
          <el-radio-button value="MINE">我的任务</el-radio-button>
        </el-radio-group>
        <el-select :model-value="filters.status" placeholder="全部状态" clearable @update:model-value="filterAndLoad({ status: ($event ?? '') as TaskFilters['status'] })">
          <el-option v-for="(label, value) in taskStatusLabels" :key="value" :label="label" :value="value" />
        </el-select>
        <el-select :model-value="filters.type" placeholder="全部类型" clearable @update:model-value="filterAndLoad({ type: ($event ?? '') as TaskFilters['type'] })">
          <el-option v-for="(label, value) in taskTypeLabels" :key="value" :label="label" :value="value" />
        </el-select>
        <el-input :model-value="filters.keyword" clearable placeholder="任务号 / 车辆 / 标题" @update:model-value="emit('update-filters', { keyword: $event })" @keyup.enter="filterAndLoad({})" @clear="filterAndLoad({})" />
        <el-button :icon="Location" :disabled="selectedCount < 2" :loading="actingTaskId === 'route'" @click="emit('optimize')">优化路线 ({{ selectedCount }})</el-button>
      </div>

      <el-table v-loading="loading" :data="pageData.items" height="100%" row-key="taskId" @selection-change="emit('selection-change', $event)">
        <el-table-column type="selection" width="44" :selectable="routeSelectable" />
        <el-table-column label="优先级" width="76">
          <template #default="{ row }"><el-tag :type="priorityTag(row.priority)" size="small">{{ taskPriorityLabels[row.priority as OperationsTaskPriority] }}</el-tag></template>
        </el-table-column>
        <el-table-column label="任务" min-width="230">
          <template #default="{ row }"><div class="task-main"><strong>{{ row.title }}</strong><span>{{ taskTypeLabels[row.taskType as OperationsTaskType] }} · {{ row.taskNo }} · {{ sourceLabels[row.sourceType as keyof typeof sourceLabels] }}</span></div></template>
        </el-table-column>
        <el-table-column label="车辆" min-width="145">
          <template #default="{ row }"><div class="vehicle-cell"><strong>{{ row.vehicleId }}</strong><span>电量 {{ batteryText(row.batteryPercent) }}<template v-if="row.duplicateCount"> · 聚合 {{ row.duplicateCount }}</template></span></div></template>
        </el-table-column>
        <el-table-column label="状态" width="108"><template #default="{ row }"><el-tag :type="statusTag(row.status)" effect="plain">{{ taskStatusLabels[row.status as OperationsTaskStatus] }}</el-tag></template></el-table-column>
        <el-table-column label="领取人" min-width="120"><template #default="{ row }"><span :class="{ unassigned: !row.assigneeName }">{{ row.assigneeName ?? '尚未领取' }}</span></template></el-table-column>
        <el-table-column label="要求完成" min-width="150"><template #default="{ row }"><span :class="{ 'overdue-text': isTaskOverdue(row) }">{{ auditTime(row.dueAt) }}</span></template></el-table-column>
        <el-table-column label="操作" width="270" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button v-if="can(row, 'claim')" type="primary" size="small" @click="emit('claim', row)">抢单</el-button>
              <el-button v-if="can(row, 'start')" type="primary" size="small" @click="emit('start', row)">开始</el-button>
              <el-button v-if="can(row, 'complete')" type="success" size="small" :icon="Check" @click="emit('complete', row)">完工</el-button>
              <el-button v-if="can(row, 'review')" type="success" size="small" :icon="CircleCheck" @click="emit('review-approve', row)">验收</el-button>
              <el-button v-if="can(row, 'resolve')" type="warning" size="small" @click="emit('resolve-reopen', row)">处理</el-button>
              <el-button v-if="can(row, 'assign')" size="small" :icon="User" @click="emit('assign', row)">{{ row.assigneeId ? '改派' : '指派' }}</el-button>
              <el-dropdown v-if="can(row, 'release') || can(row, 'exception') || can(row, 'review') || can(row, 'resolve') || can(row, 'cancel')" trigger="click">
                <el-button :icon="MoreFilled" size="small" circle aria-label="更多操作" />
                <template #dropdown><el-dropdown-menu>
                  <el-dropdown-item v-if="can(row, 'release')" @click="emit('release', row)">释放任务</el-dropdown-item>
                  <el-dropdown-item v-if="can(row, 'exception')" @click="emit('exception', row)">上报异常</el-dropdown-item>
                  <el-dropdown-item v-if="can(row, 'review')" @click="emit('review-reject', row)">退回返工</el-dropdown-item>
                  <el-dropdown-item v-if="can(row, 'resolve')" @click="emit('resolve-close', row)">关闭异常任务</el-dropdown-item>
                  <el-dropdown-item v-if="can(row, 'cancel')" divided @click="emit('cancel', row)">取消任务</el-dropdown-item>
                </el-dropdown-menu></template>
              </el-dropdown>
              <el-tooltip content="任务详情"><el-button :icon="View" size="small" circle aria-label="任务详情" @click="emit('detail', row.taskId)" /></el-tooltip>
            </div>
          </template>
        </el-table-column>
        <template #empty><el-empty description="当前条件下没有运维任务" :image-size="72" /></template>
      </el-table>
      <div class="task-pagination"><el-pagination :current-page="filters.page" :page-size="filters.pageSize" background layout="total, sizes, prev, pager, next" :total="pageData.total" :page-sizes="[10, 20, 50]" @current-change="paginate({ page: $event })" @size-change="paginate({ page: 1, pageSize: $event })" /></div>
    </section>
  </div>
</template>

<style scoped>
.task-queue { display: grid; grid-template-rows: auto minmax(0, 1fr); min-height: 0; }
.summary-band { display: grid; grid-template-columns: repeat(8, minmax(90px, 1fr)); background: #fff; border-bottom: 1px solid var(--line); }
.summary-item { position: relative; display: flex; align-items: baseline; justify-content: space-between; min-height: 66px; padding: 15px 13px; border-right: 1px solid #e3e8e6; }
.summary-item::before { position: absolute; inset: 0 auto 0 0; width: 3px; background: #83918b; content: ''; }
.summary-item span { color: var(--muted); font-size: 12px; } .summary-item strong { color: #17231f; font-size: 23px; font-variant-numeric: tabular-nums; }
.summary-item.claimed::before, .summary-item.review::before { background: #d3952c; } .summary-item.progress::before, .summary-item.mine::before { background: #2672b8; }
.summary-item.exception::before, .summary-item.overdue::before { background: #c8463c; } .summary-item.done::before { background: #27805f; }
.task-workspace { display: grid; grid-template-rows: auto minmax(0, 1fr) auto; min-height: 0; margin: 14px 18px 18px; overflow: hidden; background: #fff; border: 1px solid var(--line); border-radius: 6px; }
.task-toolbar { display: grid; grid-template-columns: auto 135px 145px minmax(180px, 1fr) auto; gap: 9px; padding: 11px 12px; border-bottom: 1px solid var(--line); }
.task-main, .vehicle-cell { display: flex; flex-direction: column; gap: 3px; } .task-main strong, .vehicle-cell strong { overflow: hidden; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.task-main span, .vehicle-cell span { margin: 0; color: var(--muted); font-size: 11px; }
.unassigned { color: #8a9490; } .overdue-text { color: #b52f28; font-weight: 600; }
.row-actions { display: flex; align-items: center; gap: 8px; min-height: 32px; }
.task-pagination { display: flex; justify-content: flex-end; padding: 10px 12px; border-top: 1px solid var(--line); }
@media (max-width: 1280px) { .summary-band { grid-template-columns: repeat(4, 1fr); } .task-toolbar { grid-template-columns: auto 125px 130px minmax(160px, 1fr); } .task-toolbar > :last-child { grid-column: 4; } }
</style>

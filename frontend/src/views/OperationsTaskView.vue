<script setup lang="ts">
import { Check, MoreFilled, Plus, Refresh, User, View } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, reactive, ref, watch } from 'vue'

import {
  assignOperationsTask,
  cancelOperationsTask,
  changeOperationsTask,
  completeOperationsTask,
  createOperationsTask,
  getOperationsAssignees,
  getOperationsSummary,
  getOperationsTask,
  getOperationsTasks,
} from '@/api/operations'
import { errorMessage } from '@/api/http'
import { getVehicles } from '@/api/vehicle'
import { useAppStore } from '@/stores/app'
import { useAuthStore } from '@/stores/auth'
import type {
  OperationsAssignee,
  OperationsTask,
  OperationsTaskDetail,
  OperationsTaskPriority,
  OperationsTaskRequest,
  OperationsTaskScope,
  OperationsTaskStatus,
  OperationsTaskSummary,
  OperationsTaskType,
} from '@/types/operations'
import type { VehicleListItem } from '@/types/vehicle'
import {
  auditTime,
  canOperateTask,
  isTaskOverdue,
  taskEventLabels,
  taskPriorityLabels,
  taskStatusLabels,
  taskTypeLabels,
} from '@/utils/operations'
import { CITIES } from '@/utils/vehicle'

const appStore = useAppStore()
const authStore = useAuthStore()
const emptySummary: OperationsTaskSummary = {
  openCount: 0, claimedCount: 0, inProgressCount: 0,
  overdueCount: 0, completedTodayCount: 0, myActiveCount: 0,
}
const pageData = ref({ items: [] as OperationsTask[], total: 0, page: 1, pageSize: 20 })
const summary = ref<OperationsTaskSummary>({ ...emptySummary })
const assignees = ref<OperationsAssignee[]>([])
const vehicles = ref<VehicleListItem[]>([])
const detail = ref<OperationsTaskDetail | null>(null)
const loading = ref(false)
const actingTaskId = ref('')
const detailVisible = ref(false)
const createVisible = ref(false)
const assignVisible = ref(false)
const selectedTask = ref<OperationsTask | null>(null)
const selectedAssigneeId = ref('')

const filters = reactive<{
  scope: OperationsTaskScope
  status: '' | OperationsTaskStatus
  type: '' | OperationsTaskType
  keyword: string
  page: number
  pageSize: number
}>({ scope: 'ALL', status: '', type: '', keyword: '', page: 1, pageSize: 20 })

const createForm = reactive<OperationsTaskRequest>({
  taskType: 'BATTERY_SWAP', priority: 'NORMAL', title: '', description: null,
  vehicleId: '', orgId: 'ORG-BJ', targetName: null, dueAt: null, assigneeId: null,
})

const currentRole = computed(() => authStore.user?.role ?? 'AUDITOR')
const currentUserId = computed(() => authStore.user?.userId ?? '')
const canWrite = computed(() => currentRole.value !== 'AUDITOR')
const summaryItems = computed(() => [
  { label: '待领取', value: summary.value.openCount, tone: 'open' },
  { label: '已领取', value: summary.value.claimedCount, tone: 'claimed' },
  { label: '执行中', value: summary.value.inProgressCount, tone: 'progress' },
  { label: '已超时', value: summary.value.overdueCount, tone: 'overdue' },
  { label: '今日完成', value: summary.value.completedTodayCount, tone: 'done' },
  { label: '我的任务', value: summary.value.myActiveCount, tone: 'mine' },
])

// ==================== 数据加载 ====================

/** 输入: 当前筛选条件; 输出: 刷新任务列表与汇总。 */
async function loadTasks() {
  loading.value = true
  try {
    const [tasks, counts] = await Promise.all([
      getOperationsTasks({
        cityCode: appStore.cityCode,
        status: filters.status || undefined,
        type: filters.type || undefined,
        scope: filters.scope,
        keyword: filters.keyword.trim() || undefined,
        page: filters.page,
        pageSize: filters.pageSize,
      }),
      getOperationsSummary(appStore.cityCode),
    ])
    pageData.value = tasks
    summary.value = counts
  } catch (cause) {
    ElMessage.error(errorMessage(cause))
  } finally {
    loading.value = false
  }
}

/** 输入: 任务编号; 输出: 打开包含完整事件时间线的详情抽屉。 */
async function openDetail(taskId: string) {
  try {
    detail.value = await getOperationsTask(taskId)
    detailVisible.value = true
  } catch (cause) {
    ElMessage.error(errorMessage(cause))
  }
}

/** 输入: 车辆搜索关键字; 输出: 当前城市最多 20 辆候选车辆。 */
async function searchVehicles(keyword = '') {
  try {
    const result = await getVehicles({
      cityCode: appStore.cityCode, keyword: keyword.trim() || undefined, page: 1, pageSize: 20,
    })
    vehicles.value = result.items
  } catch (cause) {
    ElMessage.error(errorMessage(cause))
  }
}

/** 输入: 无; 输出: 重置表单并打开新建任务弹窗。 */
async function openCreate() {
  Object.assign(createForm, {
    taskType: 'BATTERY_SWAP', priority: 'NORMAL', title: '', description: null,
    vehicleId: '', orgId: currentRole.value === 'OPERATOR'
      ? authStore.user?.orgId
      : appStore.cityCode === '110000' ? 'ORG-BJ' : 'ORG-SH',
    targetName: null, dueAt: null, assigneeId: null,
  })
  await Promise.all([searchVehicles(), loadAssignees()])
  createVisible.value = true
}

/** 输入: 当前城市; 输出: 更新可指派运维人员列表。 */
async function loadAssignees() {
  try {
    assignees.value = await getOperationsAssignees(appStore.cityCode)
  } catch (cause) {
    ElMessage.error(errorMessage(cause))
  }
}

// ==================== 创建和指派 ====================

/** 输入: 新任务表单; 输出: 创建任务并刷新队列。 */
async function submitCreate() {
  if (!createForm.title.trim() || !createForm.vehicleId || !createForm.orgId) {
    ElMessage.warning('请填写任务标题并选择车辆')
    return
  }
  actingTaskId.value = 'create'
  try {
    await createOperationsTask({
      ...createForm,
      title: createForm.title.trim(),
      description: createForm.description?.trim() || null,
      targetName: createForm.targetName?.trim() || null,
      assigneeId: currentRole.value === 'ADMIN' ? createForm.assigneeId : null,
    })
    ElMessage.success('运维任务已创建')
    createVisible.value = false
    await loadTasks()
  } catch (cause) {
    ElMessage.error(errorMessage(cause))
  } finally {
    actingTaskId.value = ''
  }
}

/** 输入: 待指派任务; 输出: 打开人员选择弹窗。 */
async function openAssignment(task: OperationsTask) {
  selectedTask.value = task
  selectedAssigneeId.value = task.assigneeId ?? ''
  await loadAssignees()
  assignVisible.value = true
}

/** 输入: 当前任务和目标人员; 输出: 完成指派或改派。 */
async function submitAssignment() {
  if (!selectedTask.value || !selectedAssigneeId.value) {
    ElMessage.warning('请选择运维人员')
    return
  }
  await runTaskAction(selectedTask.value, 'assign', async () => {
    await assignOperationsTask(selectedTask.value!.taskId, selectedAssigneeId.value)
    assignVisible.value = false
  })
}

// ==================== 任务状态流转 ====================

/**
 * 输入: 任务、动作名和 API 调用; 输出: 成功后刷新列表及已打开详情。
 * 统一串行化按钮状态，避免用户重复点击造成无意义的并发冲突。
 */
async function runTaskAction(task: OperationsTask, label: string, action: () => Promise<void>) {
  actingTaskId.value = task.taskId
  try {
    await action()
    ElMessage.success(`${label}成功`)
    await loadTasks()
    if (detailVisible.value && detail.value?.task.taskId === task.taskId) await openDetail(task.taskId)
  } catch (cause) {
    ElMessage.error(errorMessage(cause))
  } finally {
    actingTaskId.value = ''
  }
}

/** 输入: 任务; 输出: 抢单、释放或开始任务。 */
async function simpleAction(task: OperationsTask, action: 'claim' | 'release' | 'start') {
  const labels = { claim: '抢单', release: '释放', start: '开始任务' }
  await runTaskAction(task, labels[action], async () => { await changeOperationsTask(task.taskId, action) })
}

/** 输入: 执行中任务; 输出: 填写结果后完成任务。 */
async function completeTask(task: OperationsTask) {
  try {
    const { value } = await ElMessageBox.prompt('请记录处理结果，完成后车辆将恢复运营状态。', '完成任务', {
      inputPlaceholder: '例如：已更换满电电池并检查锁车功能',
      inputValidator: (text) => Boolean(text?.trim()) || '处理结果不能为空',
      confirmButtonText: '确认完成',
      cancelButtonText: '取消',
    })
    await runTaskAction(task, '完成任务', async () => { await completeOperationsTask(task.taskId, value) })
  } catch (cause) {
    if (cause !== 'cancel' && cause !== 'close') ElMessage.error(errorMessage(cause))
  }
}

/** 输入: 未结束任务; 输出: 管理员填写原因后取消任务。 */
async function cancelTask(task: OperationsTask) {
  try {
    const { value } = await ElMessageBox.prompt('取消原因会写入任务时间线。', '取消任务', {
      inputPlaceholder: '请输入取消原因',
      inputValidator: (text) => Boolean(text?.trim()) || '取消原因不能为空',
      confirmButtonText: '确认取消',
      cancelButtonText: '返回',
      type: 'warning',
    })
    await runTaskAction(task, '取消任务', async () => { await cancelOperationsTask(task.taskId, value) })
  } catch (cause) {
    if (cause !== 'cancel' && cause !== 'close') ElMessage.error(errorMessage(cause))
  }
}

// ==================== 展示辅助 ====================

/** 输入: 任务和动作; 输出: 是否允许当前登录用户执行。 */
function can(task: OperationsTask, action: Parameters<typeof canOperateTask>[3]) {
  return canOperateTask(task, currentRole.value, currentUserId.value, action)
}

/** 输入: 状态; 输出: Element Plus 标签视觉类型。 */
function statusTag(status: OperationsTaskStatus) {
  return ({ OPEN: 'info', CLAIMED: 'warning', IN_PROGRESS: 'primary', COMPLETED: 'success', CANCELLED: 'info' } as const)[status]
}

/** 输入: 优先级; 输出: Element Plus 标签视觉类型。 */
function priorityTag(priority: OperationsTaskPriority) {
  return ({ LOW: 'info', NORMAL: 'info', HIGH: 'warning', URGENT: 'danger' } as const)[priority]
}

/** 输入: 电量; 输出: 电量文字。 */
function batteryText(value: number | null) { return value === null ? '--' : `${value}%` }

watch(() => appStore.cityCode, () => { filters.page = 1; void loadTasks() })
onMounted(loadTasks)
</script>

<template>
  <div class="page-view operations-page">
    <header class="operations-heading">
      <div class="page-heading">
        <div><h1>运维任务</h1><p>换电、调度、维修与现场作业队列</p></div>
        <div class="heading-actions">
          <el-radio-group v-model="appStore.cityCode">
            <el-radio-button v-for="city in CITIES" :key="city.code" :value="city.code">{{ city.name }}</el-radio-button>
          </el-radio-group>
          <el-button v-if="canWrite" type="primary" :icon="Plus" @click="openCreate">新建任务</el-button>
          <el-tooltip content="刷新任务"><el-button :icon="Refresh" circle :loading="loading" @click="loadTasks" /></el-tooltip>
        </div>
      </div>
    </header>

    <section class="summary-band" aria-label="任务汇总">
      <div v-for="item in summaryItems" :key="item.label" :class="['summary-item', item.tone]">
        <span>{{ item.label }}</span><strong>{{ item.value }}</strong>
      </div>
    </section>

    <section class="task-workspace">
      <div class="task-toolbar">
        <el-radio-group v-model="filters.scope" @change="filters.page = 1; loadTasks()">
          <el-radio-button value="ALL">全部</el-radio-button>
          <el-radio-button value="UNASSIGNED">待领取</el-radio-button>
          <el-radio-button value="MINE">我的任务</el-radio-button>
        </el-radio-group>
        <el-select v-model="filters.status" placeholder="全部状态" clearable @change="filters.page = 1; loadTasks()">
          <el-option v-for="(label, value) in taskStatusLabels" :key="value" :label="label" :value="value" />
        </el-select>
        <el-select v-model="filters.type" placeholder="全部类型" clearable @change="filters.page = 1; loadTasks()">
          <el-option v-for="(label, value) in taskTypeLabels" :key="value" :label="label" :value="value" />
        </el-select>
        <el-input v-model="filters.keyword" clearable placeholder="任务号 / 车辆 / 标题" @keyup.enter="filters.page = 1; loadTasks()" @clear="loadTasks" />
      </div>

      <el-table v-loading="loading" :data="pageData.items" height="100%" row-key="taskId">
        <el-table-column label="优先级" width="76">
          <template #default="{ row }"><el-tag :type="priorityTag(row.priority)" size="small">{{ taskPriorityLabels[row.priority as OperationsTaskPriority] }}</el-tag></template>
        </el-table-column>
        <el-table-column label="任务" min-width="220">
          <template #default="{ row }">
            <div class="task-main"><strong>{{ row.title }}</strong><span>{{ taskTypeLabels[row.taskType as OperationsTaskType] }} · {{ row.taskNo }}</span></div>
          </template>
        </el-table-column>
        <el-table-column label="车辆" min-width="150">
          <template #default="{ row }"><div class="vehicle-cell"><strong>{{ row.vehicleId }}</strong><span>电量 {{ batteryText(row.batteryPercent) }}</span></div></template>
        </el-table-column>
        <el-table-column label="状态" width="94">
          <template #default="{ row }"><el-tag :type="statusTag(row.status)" effect="plain">{{ taskStatusLabels[row.status as OperationsTaskStatus] }}</el-tag></template>
        </el-table-column>
        <el-table-column label="领取人" min-width="125">
          <template #default="{ row }"><span :class="{ unassigned: !row.assigneeName }">{{ row.assigneeName ?? '尚未领取' }}</span></template>
        </el-table-column>
        <el-table-column label="要求完成" min-width="150">
          <template #default="{ row }"><span :class="{ 'overdue-text': isTaskOverdue(row) }">{{ auditTime(row.dueAt) }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button v-if="can(row, 'claim')" type="primary" size="small" :loading="actingTaskId === row.taskId" @click="simpleAction(row, 'claim')">抢单</el-button>
              <el-button v-if="can(row, 'start')" type="primary" size="small" :loading="actingTaskId === row.taskId" @click="simpleAction(row, 'start')">开始</el-button>
              <el-button v-if="can(row, 'complete')" type="success" size="small" :icon="Check" :loading="actingTaskId === row.taskId" @click="completeTask(row)">完成</el-button>
              <el-button v-if="can(row, 'assign')" size="small" :icon="User" @click="openAssignment(row)">{{ row.assigneeId ? '改派' : '指派' }}</el-button>
              <el-dropdown v-if="can(row, 'release') || can(row, 'cancel')" trigger="click">
                <el-button :icon="MoreFilled" size="small" circle aria-label="更多操作" />
                <template #dropdown><el-dropdown-menu>
                  <el-dropdown-item v-if="can(row, 'release')" @click="simpleAction(row, 'release')">释放任务</el-dropdown-item>
                  <el-dropdown-item v-if="can(row, 'cancel')" @click="cancelTask(row)">取消任务</el-dropdown-item>
                </el-dropdown-menu></template>
              </el-dropdown>
              <el-tooltip content="任务详情"><el-button :icon="View" size="small" circle aria-label="任务详情" @click="openDetail(row.taskId)" /></el-tooltip>
            </div>
          </template>
        </el-table-column>
        <template #empty><el-empty description="当前条件下没有运维任务" :image-size="72" /></template>
      </el-table>

      <div class="task-pagination">
        <el-pagination v-model:current-page="filters.page" v-model:page-size="filters.pageSize" background layout="total, sizes, prev, pager, next" :total="pageData.total" :page-sizes="[10, 20, 50]" @change="loadTasks" />
      </div>
    </section>

    <el-dialog v-model="createVisible" title="新建运维任务" width="680px" destroy-on-close>
      <el-form label-position="top" class="task-form">
        <el-form-item label="任务类型"><el-select v-model="createForm.taskType"><el-option v-for="(label, value) in taskTypeLabels" :key="value" :label="label" :value="value" /></el-select></el-form-item>
        <el-form-item label="优先级"><el-select v-model="createForm.priority"><el-option v-for="(label, value) in taskPriorityLabels" :key="value" :label="label" :value="value" /></el-select></el-form-item>
        <el-form-item label="任务标题" class="wide"><el-input v-model="createForm.title" maxlength="100" show-word-limit /></el-form-item>
        <el-form-item label="车辆" class="wide">
          <el-select v-model="createForm.vehicleId" filterable remote reserve-keyword :remote-method="searchVehicles" placeholder="输入车辆编号搜索">
            <el-option v-for="vehicle in vehicles" :key="vehicle.vehicleId" :label="`${vehicle.vehicleId} · 电量 ${batteryText(vehicle.latestState?.batteryPercent ?? null)}`" :value="vehicle.vehicleId" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标地点"><el-input v-model="createForm.targetName" maxlength="100" /></el-form-item>
        <el-form-item label="要求完成时间"><el-date-picker v-model="createForm.dueAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ssZ" placeholder="可选" /></el-form-item>
        <el-form-item v-if="currentRole === 'ADMIN'" label="直接指派" class="wide">
          <el-select v-model="createForm.assigneeId" clearable placeholder="留空则进入公共任务池">
            <el-option v-for="person in assignees" :key="person.userId" :label="`${person.displayName} · ${person.orgName}`" :value="person.userId" />
          </el-select>
        </el-form-item>
        <el-form-item label="任务说明" class="wide"><el-input v-model="createForm.description" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="createVisible = false">取消</el-button><el-button type="primary" :loading="actingTaskId === 'create'" @click="submitCreate">创建任务</el-button></template>
    </el-dialog>

    <el-dialog v-model="assignVisible" title="指派运维人员" width="430px">
      <el-select v-model="selectedAssigneeId" class="full-width" placeholder="选择运维人员">
        <el-option v-for="person in assignees" :key="person.userId" :label="`${person.displayName} · ${person.orgName}`" :value="person.userId" />
      </el-select>
      <template #footer><el-button @click="assignVisible = false">取消</el-button><el-button type="primary" :loading="actingTaskId === selectedTask?.taskId" @click="submitAssignment">确认指派</el-button></template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="任务详情" size="480px">
      <template v-if="detail">
        <div class="detail-head">
          <div><el-tag :type="priorityTag(detail.task.priority)" size="small">{{ taskPriorityLabels[detail.task.priority] }}</el-tag><h2>{{ detail.task.title }}</h2><p>{{ detail.task.taskNo }}</p></div>
          <el-tag :type="statusTag(detail.task.status)" effect="plain">{{ taskStatusLabels[detail.task.status] }}</el-tag>
        </div>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="类型">{{ taskTypeLabels[detail.task.taskType] }}</el-descriptions-item>
          <el-descriptions-item label="车辆">{{ detail.task.vehicleId }}</el-descriptions-item>
          <el-descriptions-item label="领取人">{{ detail.task.assigneeName ?? '尚未领取' }}</el-descriptions-item>
          <el-descriptions-item label="任务组织">{{ detail.task.orgName }}</el-descriptions-item>
          <el-descriptions-item label="目标地点" :span="2">{{ detail.task.targetName ?? '--' }}</el-descriptions-item>
          <el-descriptions-item label="要求完成" :span="2">{{ auditTime(detail.task.dueAt) }}</el-descriptions-item>
          <el-descriptions-item label="任务说明" :span="2">{{ detail.task.description ?? '--' }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.task.resultNote" label="处理结果" :span="2">{{ detail.task.resultNote }}</el-descriptions-item>
        </el-descriptions>
        <h3 class="timeline-title">操作时间线</h3>
        <el-timeline>
          <el-timeline-item v-for="event in detail.events" :key="event.eventId" :timestamp="auditTime(event.createdAt)" placement="top">
            <strong>{{ taskEventLabels[event.eventType] }}</strong>
            <p>{{ event.actorName }}<template v-if="event.note"> · {{ event.note }}</template></p>
          </el-timeline-item>
        </el-timeline>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.operations-page { display: grid; grid-template-rows: auto auto minmax(0, 1fr); background: #eef1ef; }
.operations-heading { padding: 17px 20px 15px; background: #fff; border-bottom: 1px solid var(--line); }
.heading-actions, .row-actions { display: flex; align-items: center; gap: 8px; }
.summary-band { display: grid; grid-template-columns: repeat(6, minmax(110px, 1fr)); background: #fff; border-bottom: 1px solid var(--line); }
.summary-item { position: relative; display: flex; align-items: baseline; justify-content: space-between; min-height: 70px; padding: 16px 18px; border-right: 1px solid #e3e8e6; }
.summary-item::before { position: absolute; inset: 0 auto 0 0; width: 3px; background: #83918b; content: ''; }
.summary-item span { color: var(--muted); font-size: 12px; }
.summary-item strong { color: #17231f; font-size: 25px; font-variant-numeric: tabular-nums; }
.summary-item.open::before { background: #77857f; } .summary-item.claimed::before { background: #d3952c; }
.summary-item.progress::before, .summary-item.mine::before { background: #2672b8; }
.summary-item.overdue::before { background: #c8463c; } .summary-item.done::before { background: #27805f; }
.task-workspace { display: grid; grid-template-rows: auto minmax(0, 1fr) auto; min-height: 0; margin: 14px 18px 18px; overflow: hidden; background: #fff; border: 1px solid var(--line); border-radius: 6px; }
.task-toolbar { display: grid; grid-template-columns: auto 150px 160px minmax(210px, 1fr); gap: 10px; padding: 11px 12px; border-bottom: 1px solid var(--line); }
.task-main, .vehicle-cell { display: flex; flex-direction: column; gap: 3px; }
.task-main strong, .vehicle-cell strong { overflow: hidden; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.task-main span, .vehicle-cell span { color: var(--muted); font-size: 11px; }
.unassigned { color: #8a9490; } .overdue-text { color: #b52f28; font-weight: 600; }
.row-actions { min-height: 32px; }
.task-pagination { display: flex; justify-content: flex-end; padding: 10px 12px; border-top: 1px solid var(--line); }
.task-form { display: grid; grid-template-columns: 1fr 1fr; gap: 0 16px; }
.task-form .wide { grid-column: 1 / -1; } .task-form :deep(.el-select), .task-form :deep(.el-date-editor) { width: 100%; }
.full-width { width: 100%; }
.detail-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-bottom: 18px; }
.detail-head h2 { margin: 8px 0 4px; font-size: 18px; letter-spacing: 0; }
.detail-head p, .el-timeline-item p { margin: 0; color: var(--muted); font-size: 12px; }
.timeline-title { margin: 24px 0 16px; font-size: 14px; letter-spacing: 0; }
@media (max-width: 1280px) {
  .summary-item { padding-inline: 12px; }
  .task-toolbar { grid-template-columns: auto 130px 140px minmax(180px, 1fr); }
}
</style>

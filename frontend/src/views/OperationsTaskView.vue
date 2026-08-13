<script setup lang="ts">
import {
  Check, CircleCheck, Files, Location, MoreFilled, Plus,
  Refresh, Setting, UploadFilled, User, View,
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadRequestOptions } from 'element-plus'
import { computed, onMounted, reactive, ref, watch } from 'vue'

import {
  assignOperationsTask, cancelOperationsTask, changeOperationsTask,
  completeOperationsTask, createOperationsBatch, createOperationsRule,
  createOperationsTask, getOperationsAssignees, getOperationsRules,
  getOperationsSummary, getOperationsTask, getOperationsTasks,
  optimizeOperationsRoute, reportOperationsException, resolveOperationsException,
  reviewOperationsTask, scanOperationsRules, updateOperationsRule,
  uploadOperationsAttachment,
} from '@/api/operations'
import { errorMessage } from '@/api/http'
import { getVehicles } from '@/api/vehicle'
import { useAppStore } from '@/stores/app'
import { useAuthStore } from '@/stores/auth'
import type {
  OperationsAssignee, OperationsAttachment, OperationsBatchTaskRequest,
  OperationsCompletionRequest, OperationsExceptionType, OperationsRoutePlan,
  OperationsRule, OperationsRuleRequest, OperationsTask, OperationsTaskDetail,
  OperationsTaskPriority, OperationsTaskRequest, OperationsTaskScope,
  OperationsTaskStatus, OperationsTaskSummary, OperationsTaskType,
} from '@/types/operations'
import type { VehicleListItem } from '@/types/vehicle'
import {
  auditTime, canOperateTask, exceptionTypeLabels, isTaskOverdue,
  taskEventLabels, taskPriorityLabels, taskStatusLabels, taskTypeLabels,
  triggerTypeLabels,
} from '@/utils/operations'

const appStore = useAppStore()
const authStore = useAuthStore()

const emptySummary: OperationsTaskSummary = {
  openCount: 0, claimedCount: 0, inProgressCount: 0, pendingReviewCount: 0,
  exceptionCount: 0, overdueCount: 0, completedTodayCount: 0, myActiveCount: 0,
}
const pageData = ref({ items: [] as OperationsTask[], total: 0, page: 1, pageSize: 20 })
const summary = ref<OperationsTaskSummary>({ ...emptySummary })
const assignees = ref<OperationsAssignee[]>([])
const vehicles = ref<VehicleListItem[]>([])
const detail = ref<OperationsTaskDetail | null>(null)
const rules = ref<OperationsRule[]>([])
const routePlan = ref<OperationsRoutePlan | null>(null)
const selectedRows = ref<OperationsTask[]>([])
const selectedTask = ref<OperationsTask | null>(null)
const editingRule = ref<OperationsRule | null>(null)

const loading = ref(false)
const actingTaskId = ref('')
const detailVisible = ref(false)
const createVisible = ref(false)
const batchVisible = ref(false)
const assignVisible = ref(false)
const completionVisible = ref(false)
const exceptionVisible = ref(false)
const rulesVisible = ref(false)
const ruleEditVisible = ref(false)
const routeVisible = ref(false)
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
  vehicleId: '', orgId: '', targetName: null, dueAt: null, assigneeId: null,
})
const batchForm = reactive<OperationsBatchTaskRequest>({
  batchName: '', taskType: 'REBALANCE', priority: 'NORMAL', title: '', description: null,
  vehicleIds: [], orgId: '', targetName: null, dueAt: null, assigneeId: null,
})
const completionForm = reactive<OperationsCompletionRequest>({
  resultNote: '', arrivalLongitude: 0, arrivalLatitude: 0, checklist: [],
  removedBatteryId: null, installedBatteryId: null, partsUsed: [],
  targetLongitude: null, targetLatitude: null, beforeAttachmentIds: [], afterAttachmentIds: [],
})
const completionFiles = reactive<{ before: OperationsAttachment[]; after: OperationsAttachment[] }>({
  before: [], after: [],
})
const exceptionForm = reactive<{ exceptionType: OperationsExceptionType; note: string }>({
  exceptionType: 'VEHICLE_NOT_FOUND', note: '',
})
const exceptionFiles = ref<OperationsAttachment[]>([])
const partsText = ref('')
const ruleForm = reactive<OperationsRuleRequest>({
  ruleName: '', cityCode: '', orgId: '', triggerType: 'LOW_BATTERY',
  thresholdValue: 15, taskType: 'BATTERY_SWAP', priority: 'URGENT',
  titleTemplate: '车辆{vehicleId}低电量', descriptionTemplate: '当前电量{batteryPercent}%，请尽快换电',
  dueMinutes: 60, cooldownMinutes: 30, autoClose: true, enabled: true,
})

const currentRole = computed(() => authStore.user?.role ?? 'AUDITOR')
const currentUserId = computed(() => authStore.user?.userId ?? '')
const canWrite = computed(() => currentRole.value !== 'AUDITOR')
const isAdmin = computed(() => currentRole.value === 'ADMIN')
const defaultOrgId = computed(() => currentRole.value === 'OPERATOR'
  ? authStore.user?.orgId ?? ''
  : appStore.currentCity.orgId)
const summaryItems = computed(() => [
  { label: '待领取', value: summary.value.openCount, tone: 'open' },
  { label: '已领取', value: summary.value.claimedCount, tone: 'claimed' },
  { label: '执行中', value: summary.value.inProgressCount, tone: 'progress' },
  { label: '待验收', value: summary.value.pendingReviewCount, tone: 'review' },
  { label: '异常', value: summary.value.exceptionCount, tone: 'exception' },
  { label: '已超时', value: summary.value.overdueCount, tone: 'overdue' },
  { label: '今日完成', value: summary.value.completedTodayCount, tone: 'done' },
  { label: '我的任务', value: summary.value.myActiveCount, tone: 'mine' },
])
const sourceLabels = { MANUAL: '人工', RULE: '规则', BATCH: '批量' } as const
const completionCheckOptions = computed(() => ({
  BATTERY_SWAP: ['核对车辆编号', '检查电池仓与接头', '确认新电池锁定', '确认车辆恢复供电'],
  REBALANCE: ['核对车辆编号', '确认停车区域合规', '车辆摆放整齐', '未阻塞道路或出入口'],
  REPAIR: ['核对故障现象', '完成维修或部件更换', '完成安全检查', '车辆功能复测通过'],
  INSPECTION: ['检查车身与车锁', '检查刹车与轮胎', '检查电池状态', '记录异常项'],
  RETRIEVAL: ['核对车辆编号', '记录车辆现状', '确认装车固定', '现场无遗留物'],
  CLEANING: ['完成车身清洁', '完成车篮清洁', '检查二维码可识别', '车辆摆放合规'],
} as Record<OperationsTaskType, string[]>)[selectedTask.value?.taskType ?? 'INSPECTION'])

// ==================== 数据加载 ====================

/** 输入: 当前筛选条件; 输出: 刷新任务列表与汇总。 */
async function loadTasks() {
  loading.value = true
  try {
    const [tasks, counts] = await Promise.all([
      getOperationsTasks({
        cityCode: appStore.cityCode, status: filters.status || undefined,
        type: filters.type || undefined, scope: filters.scope,
        keyword: filters.keyword.trim() || undefined, page: filters.page, pageSize: filters.pageSize,
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

/** 输入: 任务编号; 输出: 打开凭证、异常和时间线完整详情。 */
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

async function loadAssignees() {
  try {
    assignees.value = await getOperationsAssignees(appStore.cityCode)
  } catch (cause) {
    ElMessage.error(errorMessage(cause))
  }
}

// ==================== 创建、批量和规则 ====================

/** 输入: 无; 输出: 重置并打开单任务创建表单。 */
async function openCreate() {
  Object.assign(createForm, {
    taskType: 'BATTERY_SWAP', priority: 'NORMAL', title: '', description: null,
    vehicleId: '', orgId: defaultOrgId.value, targetName: null, dueAt: null, assigneeId: null,
  })
  await Promise.all([searchVehicles(), loadAssignees()])
  createVisible.value = true
}

/** 输入: 新任务表单; 输出: 创建任务并刷新队列。 */
async function submitCreate() {
  if (!createForm.title.trim() || !createForm.vehicleId || !createForm.orgId) {
    ElMessage.warning('请填写任务标题并选择车辆')
    return
  }
  actingTaskId.value = 'create'
  try {
    await createOperationsTask({ ...createForm, title: createForm.title.trim(),
      description: createForm.description?.trim() || null,
      targetName: createForm.targetName?.trim() || null,
      assigneeId: isAdmin.value ? createForm.assigneeId : null })
    ElMessage.success('运维任务已创建')
    createVisible.value = false
    await loadTasks()
  } catch (cause) {
    ElMessage.error(errorMessage(cause))
  } finally {
    actingTaskId.value = ''
  }
}

/** 输入: 无; 输出: 打开支持多车选择的批量建单表单。 */
async function openBatch() {
  Object.assign(batchForm, {
    batchName: '', taskType: 'REBALANCE', priority: 'NORMAL', title: '', description: null,
    vehicleIds: [], orgId: defaultOrgId.value, targetName: null, dueAt: null, assigneeId: null,
  })
  await Promise.all([searchVehicles(), loadAssignees()])
  batchVisible.value = true
}

/** 输入: 批量任务模板; 输出: 部分成功结果，冲突车辆逐项提示。 */
async function submitBatch() {
  if (!batchForm.batchName.trim() || !batchForm.title.trim() || batchForm.vehicleIds.length === 0) {
    ElMessage.warning('请填写批次名称、任务标题并选择车辆')
    return
  }
  actingTaskId.value = 'batch'
  try {
    const result = await createOperationsBatch({ ...batchForm,
      batchName: batchForm.batchName.trim(), title: batchForm.title.trim(),
      description: batchForm.description?.trim() || null,
      assigneeId: isAdmin.value ? batchForm.assigneeId : null })
    ElMessage.success(`批次 ${result.batchNo} 创建 ${result.createdTasks.length} 项，跳过 ${result.skipped.length} 项`)
    batchVisible.value = false
    await loadTasks()
  } catch (cause) {
    ElMessage.error(errorMessage(cause))
  } finally {
    actingTaskId.value = ''
  }
}

/** 输入: 可选已有规则; 输出: 打开新建或编辑规则表单。 */
function editRule(rule?: OperationsRule) {
  editingRule.value = rule ?? null
  Object.assign(ruleForm, rule ?? {
    ruleName: '', cityCode: appStore.cityCode, orgId: defaultOrgId.value,
    triggerType: 'LOW_BATTERY', thresholdValue: 15, taskType: 'BATTERY_SWAP',
    priority: 'URGENT', titleTemplate: '车辆{vehicleId}低电量',
    descriptionTemplate: '当前电量{batteryPercent}%，请尽快处理', dueMinutes: 60,
    cooldownMinutes: 30, autoClose: true, enabled: true,
  })
  ruleEditVisible.value = true
}

/** 输入: 当前城市; 输出: 加载并打开自动任务规则列表。 */
async function openRules() {
  try {
    rules.value = await getOperationsRules(appStore.cityCode)
    rulesVisible.value = true
  } catch (cause) {
    ElMessage.error(errorMessage(cause))
  }
}

/** 输入: 规则表单; 输出: 创建或通过版本号更新规则。 */
async function submitRule() {
  if (!ruleForm.ruleName.trim() || !ruleForm.titleTemplate.trim()) {
    ElMessage.warning('请填写规则名称和任务标题模板')
    return
  }
  actingTaskId.value = 'rule'
  try {
    const request = { ...ruleForm,
      thresholdValue: ruleForm.triggerType === 'LOW_BATTERY' ? ruleForm.thresholdValue : null,
      ruleName: ruleForm.ruleName.trim(), titleTemplate: ruleForm.titleTemplate.trim(),
      descriptionTemplate: ruleForm.descriptionTemplate?.trim() || null }
    if (editingRule.value) {
      await updateOperationsRule(editingRule.value.ruleId, editingRule.value.version, request)
    } else {
      await createOperationsRule(request)
    }
    rules.value = await getOperationsRules(appStore.cityCode)
    ruleEditVisible.value = false
    ElMessage.success(editingRule.value ? '规则已更新' : '规则已创建')
  } catch (cause) {
    ElMessage.error(errorMessage(cause))
  } finally {
    actingTaskId.value = ''
  }
}

/** 输入: 当前城市; 输出: 立即扫描车辆状态并展示建单和去重数量。 */
async function scanRulesNow() {
  actingTaskId.value = 'scan'
  try {
    const result = await scanOperationsRules(appStore.cityCode)
    ElMessage.success(`扫描 ${result.scannedVehicles} 辆，生成 ${result.createdTasks} 项，合并 ${result.deduplicatedSignals} 个重复信号`)
    await loadTasks()
  } catch (cause) {
    ElMessage.error(errorMessage(cause))
  } finally {
    actingTaskId.value = ''
  }
}

// ==================== 状态流转和闭环 ====================

/** 输入: 任务、动作名和 API 调用; 输出: 串行执行并刷新列表和详情。 */
async function runTaskAction(task: OperationsTask, label: string, action: () => Promise<void>) {
  actingTaskId.value = task.taskId
  try {
    await action()
    ElMessage.success(`${label}成功`)
    await loadTasks()
    if (detailVisible.value && detail.value?.task.taskId === task.taskId) await openDetail(task.taskId)
    return true
  } catch (cause) {
    ElMessage.error(errorMessage(cause))
    return false
  } finally {
    actingTaskId.value = ''
  }
}

async function simpleAction(task: OperationsTask, action: 'claim' | 'release' | 'start') {
  const labels = { claim: '抢单', release: '释放', start: '开始任务' }
  await runTaskAction(task, labels[action], async () => { await changeOperationsTask(task.taskId, action) })
}

/** 输入: 当前任务; 输出: 初始化结构化完工凭证。 */
function openCompletion(task: OperationsTask) {
  selectedTask.value = task
  Object.assign(completionForm, {
    resultNote: '', arrivalLongitude: task.sourceLongitude ?? 0, arrivalLatitude: task.sourceLatitude ?? 0,
    checklist: [], removedBatteryId: null, installedBatteryId: null, partsUsed: [],
    targetLongitude: null, targetLatitude: null, beforeAttachmentIds: [], afterAttachmentIds: [],
  })
  completionFiles.before = []
  completionFiles.after = []
  partsText.value = ''
  completionVisible.value = true
}

/** 输入: 浏览器定位权限; 输出: 将现场坐标写入凭证。 */
function locateForEvidence() {
  if (!navigator.geolocation) {
    ElMessage.warning('当前浏览器不支持定位')
    return
  }
  navigator.geolocation.getCurrentPosition((position) => {
    completionForm.arrivalLongitude = Number(position.coords.longitude.toFixed(7))
    completionForm.arrivalLatitude = Number(position.coords.latitude.toFixed(7))
    ElMessage.success('已读取现场位置')
  }, () => ElMessage.error('无法读取现场位置，请检查浏览器定位权限'))
}

/** 输入: 上传组件参数、附件用途和目标列表; 输出: 已上传凭证元数据。 */
async function uploadEvidence(options: UploadRequestOptions, purpose: 'BEFORE' | 'AFTER' | 'EXCEPTION', target: OperationsAttachment[]) {
  if (!selectedTask.value) return
  try {
    const attachment = await uploadOperationsAttachment(selectedTask.value.taskId, purpose, options.file)
    target.push(attachment)
    options.onSuccess(attachment)
  } catch (cause) {
    ElMessage.error(errorMessage(cause))
    throw cause
  }
}

function uploadBefore(options: UploadRequestOptions) { return uploadEvidence(options, 'BEFORE', completionFiles.before) }
function uploadAfter(options: UploadRequestOptions) { return uploadEvidence(options, 'AFTER', completionFiles.after) }
function uploadException(options: UploadRequestOptions) { return uploadEvidence(options, 'EXCEPTION', exceptionFiles.value) }

/** 输入: 完工表单; 输出: 提交凭证并进入管理员验收。 */
async function submitCompletion() {
  const task = selectedTask.value
  if (!task || !completionForm.resultNote.trim()) {
    ElMessage.warning('请填写处理结果')
    return
  }
  if (completionForm.checklist.length !== completionCheckOptions.value.length) {
    ElMessage.warning('请完成全部作业检查项')
    return
  }
  if (completionFiles.after.length === 0) {
    ElMessage.warning('至少上传一张处理后照片')
    return
  }
  completionForm.beforeAttachmentIds = completionFiles.before.map(item => item.attachmentId)
  completionForm.afterAttachmentIds = completionFiles.after.map(item => item.attachmentId)
  completionForm.partsUsed = partsText.value.split(/[，,\n]/).map(value => value.trim()).filter(Boolean)
  const succeeded = await runTaskAction(task, '提交完工', async () => {
    await completeOperationsTask(task.taskId, { ...completionForm })
  })
  if (succeeded) completionVisible.value = false
}

/** 输入: 可上报任务; 输出: 初始化现场异常表单。 */
function openException(task: OperationsTask) {
  selectedTask.value = task
  exceptionForm.exceptionType = 'VEHICLE_NOT_FOUND'
  exceptionForm.note = ''
  exceptionFiles.value = []
  exceptionVisible.value = true
}

/** 输入: 异常类型、说明和照片; 输出: 任务进入异常待处理状态。 */
async function submitException() {
  const task = selectedTask.value
  if (!task || !exceptionForm.note.trim()) {
    ElMessage.warning('请填写现场异常说明')
    return
  }
  const succeeded = await runTaskAction(task, '上报异常', async () => {
    await reportOperationsException(task.taskId, { ...exceptionForm,
      note: exceptionForm.note.trim(), attachmentIds: exceptionFiles.value.map(item => item.attachmentId) })
  })
  if (succeeded) exceptionVisible.value = false
}

/** 输入: 待验收任务和结论; 输出: 管理员通过或驳回凭证。 */
async function reviewTask(task: OperationsTask, action: 'APPROVE' | 'REJECT') {
  try {
    const { value } = await ElMessageBox.prompt(
      action === 'APPROVE' ? '可填写验收备注。' : '请说明需要返工的内容。',
      action === 'APPROVE' ? '验收通过' : '退回返工',
      { inputValidator: text => action === 'APPROVE' || Boolean(text?.trim()) || '驳回原因不能为空' },
    )
    await runTaskAction(task, action === 'APPROVE' ? '验收' : '退回', async () => {
      await reviewOperationsTask(task.taskId, action, value?.trim() ?? '')
    })
  } catch (cause) {
    if (cause !== 'cancel' && cause !== 'close') ElMessage.error(errorMessage(cause))
  }
}

/** 输入: 异常任务和处理动作; 输出: 重开继续作业或关闭任务。 */
async function resolveExceptionTask(task: OperationsTask, action: 'REOPEN' | 'CLOSE') {
  try {
    const { value } = await ElMessageBox.prompt('处理结论会保留在异常记录和任务时间线中。',
      action === 'REOPEN' ? '重开任务' : '关闭任务',
      { inputValidator: text => Boolean(text?.trim()) || '处理说明不能为空' })
    await runTaskAction(task, action === 'REOPEN' ? '重开任务' : '关闭任务', async () => {
      await resolveOperationsException(task.taskId, action, value.trim())
    })
  } catch (cause) {
    if (cause !== 'cancel' && cause !== 'close') ElMessage.error(errorMessage(cause))
  }
}

async function cancelTask(task: OperationsTask) {
  try {
    const { value } = await ElMessageBox.prompt('取消原因会写入任务时间线。', '取消任务', {
      inputValidator: text => Boolean(text?.trim()) || '取消原因不能为空', type: 'warning',
    })
    await runTaskAction(task, '取消任务', async () => { await cancelOperationsTask(task.taskId, value) })
  } catch (cause) {
    if (cause !== 'cancel' && cause !== 'close') ElMessage.error(errorMessage(cause))
  }
}

// ==================== 路线和展示 ====================

/** 输入: 表格多选任务; 输出: 高德道路距离优化后的执行顺序。 */
async function optimizeSelectedRoute() {
  if (selectedRows.value.length < 2) {
    ElMessage.warning('请至少选择两个任务')
    return
  }
  actingTaskId.value = 'route'
  try {
    routePlan.value = await optimizeOperationsRoute(selectedRows.value.map(task => task.taskId))
    routeVisible.value = true
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

async function submitAssignment() {
  if (!selectedTask.value || !selectedAssigneeId.value) return
  await runTaskAction(selectedTask.value, '指派', async () => {
    await assignOperationsTask(selectedTask.value!.taskId, selectedAssigneeId.value)
    assignVisible.value = false
  })
}

function can(task: OperationsTask, action: Parameters<typeof canOperateTask>[3]) {
  return canOperateTask(task, currentRole.value, currentUserId.value, action)
}

function routeSelectable(task: OperationsTask) {
  return ['OPEN', 'CLAIMED', 'IN_PROGRESS'].includes(task.status)
}

function statusTag(status: OperationsTaskStatus) {
  return ({ OPEN: 'info', CLAIMED: 'warning', IN_PROGRESS: 'primary', PENDING_REVIEW: 'warning',
    EXCEPTION: 'danger', COMPLETED: 'success', CANCELLED: 'info' } as const)[status]
}

function priorityTag(priority: OperationsTaskPriority) {
  return ({ LOW: 'info', NORMAL: 'info', HIGH: 'warning', URGENT: 'danger' } as const)[priority]
}

function batteryText(value: number | null) { return value === null ? '--' : `${value}%` }
function formatDistance(value: number) { return value >= 1000 ? `${(value / 1000).toFixed(1)} km` : `${value} m` }
function formatDuration(value: number) { return value >= 3600 ? `${(value / 3600).toFixed(1)} 小时` : `${Math.ceil(value / 60)} 分钟` }

watch(() => appStore.cityCode, () => { filters.page = 1; selectedRows.value = []; void loadTasks() })
onMounted(loadTasks)
</script>

<template>
  <div class="page-view operations-page">
    <header class="operations-heading">
      <div class="page-heading">
        <div><h1>运维任务</h1><p>换电、调度、维修与现场作业队列</p></div>
        <div class="heading-actions">
          <el-radio-group v-model="appStore.cityCode">
            <el-radio-button v-for="city in appStore.cities" :key="city.code" :value="city.code">{{ city.name }}</el-radio-button>
          </el-radio-group>
          <el-tooltip v-if="isAdmin" content="自动任务规则"><el-button :icon="Setting" circle @click="openRules" /></el-tooltip>
          <el-button v-if="canWrite" :icon="Files" @click="openBatch">批量任务</el-button>
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
        <el-button :icon="Location" :disabled="selectedRows.length < 2" :loading="actingTaskId === 'route'" @click="optimizeSelectedRoute">优化路线 ({{ selectedRows.length }})</el-button>
      </div>

      <el-table v-loading="loading" :data="pageData.items" height="100%" row-key="taskId" @selection-change="selectedRows = $event">
        <el-table-column type="selection" width="44" :selectable="routeSelectable" />
        <el-table-column label="优先级" width="76">
          <template #default="{ row }"><el-tag :type="priorityTag(row.priority)" size="small">{{ taskPriorityLabels[row.priority as OperationsTaskPriority] }}</el-tag></template>
        </el-table-column>
        <el-table-column label="任务" min-width="230">
          <template #default="{ row }">
            <div class="task-main"><strong>{{ row.title }}</strong><span>{{ taskTypeLabels[row.taskType as OperationsTaskType] }} · {{ row.taskNo }} · {{ sourceLabels[row.sourceType as keyof typeof sourceLabels] }}</span></div>
          </template>
        </el-table-column>
        <el-table-column label="车辆" min-width="145">
          <template #default="{ row }"><div class="vehicle-cell"><strong>{{ row.vehicleId }}</strong><span>电量 {{ batteryText(row.batteryPercent) }}<template v-if="row.duplicateCount"> · 聚合 {{ row.duplicateCount }}</template></span></div></template>
        </el-table-column>
        <el-table-column label="状态" width="108">
          <template #default="{ row }"><el-tag :type="statusTag(row.status)" effect="plain">{{ taskStatusLabels[row.status as OperationsTaskStatus] }}</el-tag></template>
        </el-table-column>
        <el-table-column label="领取人" min-width="120"><template #default="{ row }"><span :class="{ unassigned: !row.assigneeName }">{{ row.assigneeName ?? '尚未领取' }}</span></template></el-table-column>
        <el-table-column label="要求完成" min-width="150"><template #default="{ row }"><span :class="{ 'overdue-text': isTaskOverdue(row) }">{{ auditTime(row.dueAt) }}</span></template></el-table-column>
        <el-table-column label="操作" width="270" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button v-if="can(row, 'claim')" type="primary" size="small" @click="simpleAction(row, 'claim')">抢单</el-button>
              <el-button v-if="can(row, 'start')" type="primary" size="small" @click="simpleAction(row, 'start')">开始</el-button>
              <el-button v-if="can(row, 'complete')" type="success" size="small" :icon="Check" @click="openCompletion(row)">完工</el-button>
              <el-button v-if="can(row, 'review')" type="success" size="small" :icon="CircleCheck" @click="reviewTask(row, 'APPROVE')">验收</el-button>
              <el-button v-if="can(row, 'resolve')" type="warning" size="small" @click="resolveExceptionTask(row, 'REOPEN')">处理</el-button>
              <el-button v-if="can(row, 'assign')" size="small" :icon="User" @click="openAssignment(row)">{{ row.assigneeId ? '改派' : '指派' }}</el-button>
              <el-dropdown v-if="can(row, 'release') || can(row, 'exception') || can(row, 'review') || can(row, 'resolve') || can(row, 'cancel')" trigger="click">
                <el-button :icon="MoreFilled" size="small" circle aria-label="更多操作" />
                <template #dropdown><el-dropdown-menu>
                  <el-dropdown-item v-if="can(row, 'release')" @click="simpleAction(row, 'release')">释放任务</el-dropdown-item>
                  <el-dropdown-item v-if="can(row, 'exception')" @click="openException(row)">上报异常</el-dropdown-item>
                  <el-dropdown-item v-if="can(row, 'review')" @click="reviewTask(row, 'REJECT')">退回返工</el-dropdown-item>
                  <el-dropdown-item v-if="can(row, 'resolve')" @click="resolveExceptionTask(row, 'CLOSE')">关闭异常任务</el-dropdown-item>
                  <el-dropdown-item v-if="can(row, 'cancel')" divided @click="cancelTask(row)">取消任务</el-dropdown-item>
                </el-dropdown-menu></template>
              </el-dropdown>
              <el-tooltip content="任务详情"><el-button :icon="View" size="small" circle aria-label="任务详情" @click="openDetail(row.taskId)" /></el-tooltip>
            </div>
          </template>
        </el-table-column>
        <template #empty><el-empty description="当前条件下没有运维任务" :image-size="72" /></template>
      </el-table>
      <div class="task-pagination"><el-pagination v-model:current-page="filters.page" v-model:page-size="filters.pageSize" background layout="total, sizes, prev, pager, next" :total="pageData.total" :page-sizes="[10, 20, 50]" @change="loadTasks" /></div>
    </section>

    <el-dialog v-model="createVisible" title="新建运维任务" width="680px" destroy-on-close>
      <el-form label-position="top" class="task-form">
        <el-form-item label="任务类型"><el-select v-model="createForm.taskType"><el-option v-for="(label, value) in taskTypeLabels" :key="value" :label="label" :value="value" /></el-select></el-form-item>
        <el-form-item label="优先级"><el-select v-model="createForm.priority"><el-option v-for="(label, value) in taskPriorityLabels" :key="value" :label="label" :value="value" /></el-select></el-form-item>
        <el-form-item label="任务标题" class="wide"><el-input v-model="createForm.title" maxlength="100" show-word-limit /></el-form-item>
        <el-form-item label="车辆" class="wide"><el-select v-model="createForm.vehicleId" filterable remote reserve-keyword :remote-method="searchVehicles" placeholder="输入车辆编号搜索"><el-option v-for="vehicle in vehicles" :key="vehicle.vehicleId" :label="`${vehicle.vehicleId} · 电量 ${batteryText(vehicle.latestState?.batteryPercent ?? null)}`" :value="vehicle.vehicleId" /></el-select></el-form-item>
        <el-form-item label="目标地点"><el-input v-model="createForm.targetName" maxlength="100" /></el-form-item>
        <el-form-item label="要求完成时间"><el-date-picker v-model="createForm.dueAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ssZ" placeholder="可选" /></el-form-item>
        <el-form-item v-if="isAdmin" label="直接指派" class="wide"><el-select v-model="createForm.assigneeId" clearable placeholder="留空则进入公共任务池"><el-option v-for="person in assignees" :key="person.userId" :label="`${person.displayName} · ${person.orgName}`" :value="person.userId" /></el-select></el-form-item>
        <el-form-item label="任务说明" class="wide"><el-input v-model="createForm.description" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="createVisible = false">取消</el-button><el-button type="primary" :loading="actingTaskId === 'create'" @click="submitCreate">创建任务</el-button></template>
    </el-dialog>

    <el-dialog v-model="batchVisible" title="批量创建任务" width="700px" destroy-on-close>
      <el-form label-position="top" class="task-form">
        <el-form-item label="批次名称"><el-input v-model="batchForm.batchName" maxlength="100" /></el-form-item>
        <el-form-item label="任务类型"><el-select v-model="batchForm.taskType"><el-option v-for="(label, value) in taskTypeLabels" :key="value" :label="label" :value="value" /></el-select></el-form-item>
        <el-form-item label="任务标题" class="wide"><el-input v-model="batchForm.title" maxlength="100" /></el-form-item>
        <el-form-item label="车辆（最多 200 辆）" class="wide"><el-select v-model="batchForm.vehicleIds" multiple filterable remote reserve-keyword :remote-method="searchVehicles" placeholder="搜索并选择车辆"><el-option v-for="vehicle in vehicles" :key="vehicle.vehicleId" :label="`${vehicle.vehicleId} · 电量 ${batteryText(vehicle.latestState?.batteryPercent ?? null)}`" :value="vehicle.vehicleId" /></el-select></el-form-item>
        <el-form-item label="优先级"><el-select v-model="batchForm.priority"><el-option v-for="(label, value) in taskPriorityLabels" :key="value" :label="label" :value="value" /></el-select></el-form-item>
        <el-form-item label="要求完成时间"><el-date-picker v-model="batchForm.dueAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ssZ" placeholder="可选" /></el-form-item>
        <el-form-item v-if="isAdmin" label="直接指派" class="wide"><el-select v-model="batchForm.assigneeId" clearable><el-option v-for="person in assignees" :key="person.userId" :label="`${person.displayName} · ${person.orgName}`" :value="person.userId" /></el-select></el-form-item>
        <el-form-item label="任务说明" class="wide"><el-input v-model="batchForm.description" type="textarea" :rows="3" maxlength="500" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="batchVisible = false">取消</el-button><el-button type="primary" :loading="actingTaskId === 'batch'" @click="submitBatch">创建批次</el-button></template>
    </el-dialog>

    <el-dialog v-model="completionVisible" title="提交完工作业凭证" width="760px" destroy-on-close>
      <el-form label-position="top" class="evidence-form">
        <el-form-item label="处理结果" class="wide"><el-input v-model="completionForm.resultNote" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
        <el-form-item label="现场经度"><el-input-number v-model="completionForm.arrivalLongitude" :precision="7" :controls="false" /></el-form-item>
        <el-form-item label="现场纬度"><div class="location-field"><el-input-number v-model="completionForm.arrivalLatitude" :precision="7" :controls="false" /><el-tooltip content="读取当前位置"><el-button :icon="Location" circle @click="locateForEvidence" /></el-tooltip></div></el-form-item>
        <el-form-item label="作业检查" class="wide"><el-checkbox-group v-model="completionForm.checklist" class="check-grid"><el-checkbox v-for="item in completionCheckOptions" :key="item" :value="item">{{ item }}</el-checkbox></el-checkbox-group></el-form-item>
        <template v-if="selectedTask?.taskType === 'BATTERY_SWAP'">
          <el-form-item label="换出电池编号"><el-input v-model="completionForm.removedBatteryId" /></el-form-item>
          <el-form-item label="换入电池编号"><el-input v-model="completionForm.installedBatteryId" /></el-form-item>
        </template>
        <template v-if="selectedTask?.taskType === 'REBALANCE'">
          <el-form-item label="实际停放经度"><el-input-number v-model="completionForm.targetLongitude" :precision="7" :controls="false" /></el-form-item>
          <el-form-item label="实际停放纬度"><el-input-number v-model="completionForm.targetLatitude" :precision="7" :controls="false" /></el-form-item>
        </template>
        <el-form-item v-if="selectedTask?.taskType === 'REPAIR'" label="使用物料" class="wide"><el-input v-model="partsText" placeholder="多个物料使用逗号分隔" /></el-form-item>
        <el-form-item label="处理前照片" class="wide"><div class="upload-row"><el-upload :http-request="uploadBefore" :show-file-list="false" accept="image/jpeg,image/png"><el-button :icon="UploadFilled">上传照片</el-button></el-upload><a v-for="file in completionFiles.before" :key="file.attachmentId" :href="file.downloadUrl" target="_blank">{{ file.originalName }}</a></div></el-form-item>
        <el-form-item label="处理后照片（必填）" class="wide"><div class="upload-row"><el-upload :http-request="uploadAfter" :show-file-list="false" accept="image/jpeg,image/png"><el-button type="primary" plain :icon="UploadFilled">上传照片</el-button></el-upload><a v-for="file in completionFiles.after" :key="file.attachmentId" :href="file.downloadUrl" target="_blank">{{ file.originalName }}</a></div></el-form-item>
      </el-form>
      <template #footer><el-button @click="completionVisible = false">取消</el-button><el-button type="primary" :loading="actingTaskId === selectedTask?.taskId" @click="submitCompletion">提交验收</el-button></template>
    </el-dialog>

    <el-dialog v-model="exceptionVisible" title="上报现场异常" width="560px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="异常类型"><el-select v-model="exceptionForm.exceptionType" class="full-width"><el-option v-for="(label, value) in exceptionTypeLabels" :key="value" :label="label" :value="value" /></el-select></el-form-item>
        <el-form-item label="现场说明"><el-input v-model="exceptionForm.note" type="textarea" :rows="4" maxlength="500" show-word-limit /></el-form-item>
        <el-form-item label="现场照片"><div class="upload-row"><el-upload :http-request="uploadException" :show-file-list="false" accept="image/jpeg,image/png"><el-button :icon="UploadFilled">上传照片</el-button></el-upload><a v-for="file in exceptionFiles" :key="file.attachmentId" :href="file.downloadUrl" target="_blank">{{ file.originalName }}</a></div></el-form-item>
      </el-form>
      <template #footer><el-button @click="exceptionVisible = false">取消</el-button><el-button type="danger" :loading="actingTaskId === selectedTask?.taskId" @click="submitException">确认上报</el-button></template>
    </el-dialog>

    <el-dialog v-model="assignVisible" title="指派运维人员" width="430px"><el-select v-model="selectedAssigneeId" class="full-width"><el-option v-for="person in assignees" :key="person.userId" :label="`${person.displayName} · ${person.orgName}`" :value="person.userId" /></el-select><template #footer><el-button @click="assignVisible = false">取消</el-button><el-button type="primary" @click="submitAssignment">确认指派</el-button></template></el-dialog>

    <el-dialog v-model="rulesVisible" title="自动任务规则" width="920px">
      <div class="dialog-tools"><el-button :icon="Refresh" :loading="actingTaskId === 'scan'" @click="scanRulesNow">立即扫描</el-button><el-button type="primary" :icon="Plus" @click="editRule()">新建规则</el-button></div>
      <el-table :data="rules" max-height="480">
        <el-table-column prop="ruleName" label="规则" min-width="160" />
        <el-table-column label="触发" min-width="130"><template #default="{ row }">{{ triggerTypeLabels[row.triggerType as keyof typeof triggerTypeLabels] }}<template v-if="row.thresholdValue !== null"> ≤ {{ row.thresholdValue }}%</template></template></el-table-column>
        <el-table-column label="生成任务" min-width="140"><template #default="{ row }">{{ taskTypeLabels[row.taskType as OperationsTaskType] }}</template></el-table-column>
        <el-table-column prop="orgName" label="执行组织" min-width="140" />
        <el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'" effect="plain">{{ row.enabled ? '启用' : '停用' }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="84"><template #default="{ row }"><el-button text type="primary" @click="editRule(row)">编辑</el-button></template></el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="ruleEditVisible" :title="editingRule ? '编辑自动任务规则' : '新建自动任务规则'" width="720px" destroy-on-close>
      <el-form label-position="top" class="task-form">
        <el-form-item label="规则名称"><el-input v-model="ruleForm.ruleName" maxlength="100" /></el-form-item>
        <el-form-item label="触发类型"><el-select v-model="ruleForm.triggerType"><el-option v-for="(label, value) in triggerTypeLabels" :key="value" :label="label" :value="value" /></el-select></el-form-item>
        <el-form-item v-if="ruleForm.triggerType === 'LOW_BATTERY'" label="电量阈值"><el-input-number v-model="ruleForm.thresholdValue" :min="1" :max="99" /></el-form-item>
        <el-form-item label="生成任务类型"><el-select v-model="ruleForm.taskType"><el-option v-for="(label, value) in taskTypeLabels" :key="value" :label="label" :value="value" /></el-select></el-form-item>
        <el-form-item label="任务标题模板" class="wide"><el-input v-model="ruleForm.titleTemplate" maxlength="100" /></el-form-item>
        <el-form-item label="任务说明模板" class="wide"><el-input v-model="ruleForm.descriptionTemplate" type="textarea" :rows="2" maxlength="500" /></el-form-item>
        <el-form-item label="完成时限（分钟）"><el-input-number v-model="ruleForm.dueMinutes" :min="5" :max="10080" /></el-form-item>
        <el-form-item label="冷却时间（分钟）"><el-input-number v-model="ruleForm.cooldownMinutes" :min="0" :max="10080" /></el-form-item>
        <el-form-item label="规则设置" class="wide"><el-checkbox v-model="ruleForm.autoClose">状态恢复时自动关闭未开工任务</el-checkbox><el-checkbox v-model="ruleForm.enabled">启用规则</el-checkbox></el-form-item>
      </el-form>
      <template #footer><el-button @click="ruleEditVisible = false">取消</el-button><el-button type="primary" :loading="actingTaskId === 'rule'" @click="submitRule">保存规则</el-button></template>
    </el-dialog>

    <el-dialog v-model="routeVisible" title="作业路线" width="720px">
      <template v-if="routePlan"><el-alert v-if="routePlan.warning" :title="routePlan.warning" type="warning" :closable="false" show-icon /><div class="route-summary"><strong>{{ formatDistance(routePlan.totalDistanceMeters) }}</strong><span>预计 {{ formatDuration(routePlan.totalDurationSeconds) }} · {{ routePlan.provider === 'AMAP' ? '高德道路数据' : '本地估算' }}</span></div><el-table :data="routePlan.stops" max-height="430"><el-table-column prop="sequence" label="顺序" width="64" /><el-table-column prop="vehicleId" label="车辆" min-width="130" /><el-table-column prop="title" label="任务" min-width="180" /><el-table-column label="路段" width="160"><template #default="{ row }">{{ formatDistance(row.legDistanceMeters) }} · {{ formatDuration(row.legDurationSeconds) }}</template></el-table-column></el-table></template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="任务详情" size="520px">
      <template v-if="detail">
        <div class="detail-head"><div><el-tag :type="priorityTag(detail.task.priority)" size="small">{{ taskPriorityLabels[detail.task.priority] }}</el-tag><h2>{{ detail.task.title }}</h2><p>{{ detail.task.taskNo }} · {{ sourceLabels[detail.task.sourceType] }}</p></div><el-tag :type="statusTag(detail.task.status)" effect="plain">{{ taskStatusLabels[detail.task.status] }}</el-tag></div>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="类型">{{ taskTypeLabels[detail.task.taskType] }}</el-descriptions-item><el-descriptions-item label="车辆">{{ detail.task.vehicleId }}</el-descriptions-item>
          <el-descriptions-item label="领取人">{{ detail.task.assigneeName ?? '尚未领取' }}</el-descriptions-item><el-descriptions-item label="组织">{{ detail.task.orgName }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.task.ruleName" label="触发规则" :span="2">{{ detail.task.ruleName }} · 聚合 {{ detail.task.duplicateCount }} 次</el-descriptions-item>
          <el-descriptions-item label="要求完成" :span="2">{{ auditTime(detail.task.dueAt) }}</el-descriptions-item><el-descriptions-item label="任务说明" :span="2">{{ detail.task.description ?? '--' }}</el-descriptions-item>
        </el-descriptions>

        <template v-if="detail.triggers.length"><h3 class="section-title">规则触发</h3><div class="record-row" v-for="trigger in detail.triggers" :key="trigger.triggerId"><el-tag :type="trigger.active ? 'danger' : 'success'" size="small">{{ trigger.active ? '触发中' : '已恢复' }}</el-tag><div><strong>{{ trigger.ruleName }}</strong><p>累计 {{ trigger.occurrenceCount }} 次 · 最近 {{ auditTime(trigger.lastTriggeredAt) }}</p></div></div></template>
        <template v-if="detail.evidence.length"><h3 class="section-title">作业凭证</h3><div class="evidence-record" v-for="item in detail.evidence" :key="item.evidenceId"><div class="record-title"><strong>第 {{ item.submissionNo }} 次提交</strong><el-tag :type="item.reviewStatus === 'APPROVED' ? 'success' : item.reviewStatus === 'REJECTED' ? 'danger' : 'warning'" size="small">{{ item.reviewStatus === 'APPROVED' ? '已通过' : item.reviewStatus === 'REJECTED' ? '已退回' : '待验收' }}</el-tag></div><p>{{ item.resultNote }}</p><p>{{ item.submittedByName }} · {{ auditTime(item.submittedAt) }}</p><div class="attachment-links"><a v-for="file in item.attachments" :key="file.attachmentId" :href="file.downloadUrl" target="_blank">{{ file.purpose === 'BEFORE' ? '处理前' : '处理后' }}：{{ file.originalName }}</a></div></div></template>
        <template v-if="detail.exceptions.length"><h3 class="section-title">异常闭环</h3><div class="evidence-record exception-record" v-for="item in detail.exceptions" :key="item.exceptionId"><div class="record-title"><strong>{{ exceptionTypeLabels[item.exceptionType] }}</strong><el-tag :type="item.resolvedAt ? 'success' : 'danger'" size="small">{{ item.resolvedAt ? '已处理' : '待处理' }}</el-tag></div><p>{{ item.note }}</p><p>{{ item.reportedByName }} · {{ auditTime(item.reportedAt) }}</p><p v-if="item.resolutionNote">处理结论：{{ item.resolutionNote }}</p><div class="attachment-links"><a v-for="file in item.attachments" :key="file.attachmentId" :href="file.downloadUrl" target="_blank">{{ file.originalName }}</a></div></div></template>

        <h3 class="section-title">操作时间线</h3><el-timeline><el-timeline-item v-for="event in detail.events" :key="event.eventId" :timestamp="auditTime(event.createdAt)" placement="top"><strong>{{ taskEventLabels[event.eventType] }}</strong><p>{{ event.actorName }}<template v-if="event.note"> · {{ event.note }}</template></p></el-timeline-item></el-timeline>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.operations-page { display: grid; grid-template-rows: auto auto minmax(0, 1fr); background: #eef1ef; }
.operations-heading { padding: 17px 20px 15px; background: #fff; border-bottom: 1px solid var(--line); }
.heading-actions, .row-actions, .dialog-tools, .upload-row, .location-field { display: flex; align-items: center; gap: 8px; }
.heading-actions { flex-wrap: wrap; justify-content: flex-end; }
.summary-band { display: grid; grid-template-columns: repeat(8, minmax(90px, 1fr)); background: #fff; border-bottom: 1px solid var(--line); }
.summary-item { position: relative; display: flex; align-items: baseline; justify-content: space-between; min-height: 66px; padding: 15px 13px; border-right: 1px solid #e3e8e6; }
.summary-item::before { position: absolute; inset: 0 auto 0 0; width: 3px; background: #83918b; content: ''; }
.summary-item span { color: var(--muted); font-size: 12px; } .summary-item strong { color: #17231f; font-size: 23px; font-variant-numeric: tabular-nums; }
.summary-item.claimed::before, .summary-item.review::before { background: #d3952c; } .summary-item.progress::before, .summary-item.mine::before { background: #2672b8; }
.summary-item.exception::before, .summary-item.overdue::before { background: #c8463c; } .summary-item.done::before { background: #27805f; }
.task-workspace { display: grid; grid-template-rows: auto minmax(0, 1fr) auto; min-height: 0; margin: 14px 18px 18px; overflow: hidden; background: #fff; border: 1px solid var(--line); border-radius: 6px; }
.task-toolbar { display: grid; grid-template-columns: auto 135px 145px minmax(180px, 1fr) auto; gap: 9px; padding: 11px 12px; border-bottom: 1px solid var(--line); }
.task-main, .vehicle-cell { display: flex; flex-direction: column; gap: 3px; } .task-main strong, .vehicle-cell strong { overflow: hidden; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.task-main span, .vehicle-cell span, .detail-head p, .el-timeline-item p, .evidence-record p, .record-row p { margin: 0; color: var(--muted); font-size: 11px; }
.unassigned { color: #8a9490; } .overdue-text { color: #b52f28; font-weight: 600; } .row-actions { min-height: 32px; }
.task-pagination { display: flex; justify-content: flex-end; padding: 10px 12px; border-top: 1px solid var(--line); }
.task-form, .evidence-form { display: grid; grid-template-columns: 1fr 1fr; gap: 0 16px; } .task-form .wide, .evidence-form .wide { grid-column: 1 / -1; }
.task-form :deep(.el-select), .task-form :deep(.el-date-editor), .evidence-form :deep(.el-input-number) { width: 100%; }
.full-width { width: 100%; } .dialog-tools { justify-content: flex-end; margin-bottom: 12px; }
.check-grid { display: grid; grid-template-columns: 1fr 1fr; width: 100%; } .upload-row { flex-wrap: wrap; } .upload-row a, .attachment-links a { color: #1769a8; font-size: 12px; text-decoration: none; }
.detail-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-bottom: 18px; } .detail-head h2 { margin: 8px 0 4px; font-size: 18px; letter-spacing: 0; }
.section-title { margin: 22px 0 12px; font-size: 14px; letter-spacing: 0; } .record-row { display: flex; align-items: flex-start; gap: 10px; padding: 10px 0; border-bottom: 1px solid #edf0ef; }
.evidence-record { padding: 11px 12px; border-left: 3px solid #27805f; background: #f6f8f7; margin-bottom: 9px; } .exception-record { border-left-color: #c8463c; }
.record-title { display: flex; align-items: center; justify-content: space-between; margin-bottom: 6px; } .attachment-links { display: flex; flex-direction: column; gap: 3px; margin-top: 6px; }
.route-summary { display: flex; align-items: baseline; gap: 12px; padding: 16px 2px 12px; } .route-summary strong { font-size: 24px; } .route-summary span { color: var(--muted); font-size: 12px; }
@media (max-width: 1280px) { .summary-band { grid-template-columns: repeat(4, 1fr); } .task-toolbar { grid-template-columns: auto 125px 130px minmax(160px, 1fr); } .task-toolbar > :last-child { grid-column: 4; } }
</style>

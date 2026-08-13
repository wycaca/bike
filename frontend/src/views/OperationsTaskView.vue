<script setup lang="ts">
import {
  Files, Location, Plus, Refresh, Setting, UploadFilled,
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
import OperationsTaskDetailDrawer from '@/components/operations/OperationsTaskDetailDrawer.vue'
import OperationsTaskQueue from '@/components/operations/OperationsTaskQueue.vue'
import { useAppStore } from '@/stores/app'
import { useAuthStore } from '@/stores/auth'
import type {
  OperationsAssignee, OperationsAttachment, OperationsBatchTaskRequest,
  OperationsCompletionRequest, OperationsExceptionType, OperationsRoutePlan,
  OperationsRule, OperationsRuleRequest, OperationsTask, OperationsTaskDetail,
  OperationsTaskRequest, OperationsTaskScope,
  OperationsTaskStatus, OperationsTaskSummary, OperationsTaskType,
} from '@/types/operations'
import type { VehicleListItem } from '@/types/vehicle'
import {
  exceptionTypeLabels, taskPriorityLabels, taskTypeLabels, triggerTypeLabels,
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

interface TaskFilters {
  scope: OperationsTaskScope
  status: '' | OperationsTaskStatus
  type: '' | OperationsTaskType
  keyword: string
  page: number
  pageSize: number
}

const filters = reactive<TaskFilters>({ scope: 'ALL', status: '', type: '', keyword: '', page: 1, pageSize: 20 })

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

/** 输入: 队列组件产生的局部筛选条件; 输出: 合并到页面查询状态。 */
function updateFilters(patch: Partial<TaskFilters>) { Object.assign(filters, patch) }

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

    <OperationsTaskQueue
      :summary="summary" :page-data="pageData" :filters="filters" :loading="loading"
      :selected-count="selectedRows.length" :acting-task-id="actingTaskId"
      :current-role="currentRole" :current-user-id="currentUserId"
      @update-filters="updateFilters" @selection-change="selectedRows = $event" @load="loadTasks"
      @optimize="optimizeSelectedRoute" @claim="simpleAction($event, 'claim')" @start="simpleAction($event, 'start')"
      @complete="openCompletion" @review-approve="reviewTask($event, 'APPROVE')"
      @resolve-reopen="resolveExceptionTask($event, 'REOPEN')" @assign="openAssignment"
      @release="simpleAction($event, 'release')" @exception="openException"
      @review-reject="reviewTask($event, 'REJECT')" @resolve-close="resolveExceptionTask($event, 'CLOSE')"
      @cancel="cancelTask" @detail="openDetail"
    />

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

    <OperationsTaskDetailDrawer v-model:visible="detailVisible" :detail="detail" />
  </div>
</template>

<style scoped>
.operations-page { display: grid; grid-template-rows: auto minmax(0, 1fr); background: #eef1ef; }
.operations-heading { padding: 17px 20px 15px; background: #fff; border-bottom: 1px solid var(--line); }
.heading-actions, .dialog-tools, .upload-row, .location-field { display: flex; align-items: center; gap: 8px; }
.heading-actions { flex-wrap: wrap; justify-content: flex-end; }
.task-form, .evidence-form { display: grid; grid-template-columns: 1fr 1fr; gap: 0 16px; } .task-form .wide, .evidence-form .wide { grid-column: 1 / -1; }
.task-form :deep(.el-select), .task-form :deep(.el-date-editor), .evidence-form :deep(.el-input-number) { width: 100%; }
.full-width { width: 100%; } .dialog-tools { justify-content: flex-end; margin-bottom: 12px; }
.check-grid { display: grid; grid-template-columns: 1fr 1fr; width: 100%; } .upload-row { flex-wrap: wrap; } .upload-row a { color: #1769a8; font-size: 12px; text-decoration: none; }
.route-summary { display: flex; align-items: baseline; gap: 12px; padding: 16px 2px 12px; } .route-summary strong { font-size: 24px; } .route-summary span { color: var(--muted); font-size: 12px; }
</style>

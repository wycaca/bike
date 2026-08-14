<script setup lang="ts">
import {
  Files, Plus, Refresh, Setting,
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, reactive, ref, watch } from 'vue'

import {
  assignOperationsTask, cancelOperationsTask, changeOperationsTask,
  createOperationsRule, getOperationsAssignees, getOperationsRules,
  getOperationsSummary, getOperationsTask, getOperationsTasks,
  optimizeOperationsRoute, resolveOperationsException,
  reviewOperationsTask, scanOperationsRules, updateOperationsRule,
} from '@/api/operations'
import { errorMessage } from '@/api/http'
import OperationsTaskCreateDialogs from '@/components/operations/OperationsTaskCreateDialogs.vue'
import OperationsTaskDetailDrawer from '@/components/operations/OperationsTaskDetailDrawer.vue'
import OperationsTaskEvidenceDialogs from '@/components/operations/OperationsTaskEvidenceDialogs.vue'
import OperationsTaskQueue from '@/components/operations/OperationsTaskQueue.vue'
import { useAppStore } from '@/stores/app'
import { useAuthStore } from '@/stores/auth'
import type {
  OperationsAssignee, OperationsRoutePlan,
  OperationsRule, OperationsRuleRequest, OperationsTask, OperationsTaskDetail,
  OperationsTaskScope,
  OperationsTaskStatus, OperationsTaskSummary, OperationsTaskType,
} from '@/types/operations'
import {
  taskTypeLabels, triggerTypeLabels,
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
const detail = ref<OperationsTaskDetail | null>(null)
const rules = ref<OperationsRule[]>([])
const routePlan = ref<OperationsRoutePlan | null>(null)
const selectedRows = ref<OperationsTask[]>([])
const selectedTask = ref<OperationsTask | null>(null)
const editingRule = ref<OperationsRule | null>(null)
const createDialogs = ref<InstanceType<typeof OperationsTaskCreateDialogs>>()
const evidenceDialogs = ref<InstanceType<typeof OperationsTaskEvidenceDialogs>>()

const loading = ref(false)
const actingTaskId = ref('')
const detailVisible = ref(false)
const assignVisible = ref(false)
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

async function loadAssignees() {
  try {
    assignees.value = await getOperationsAssignees(appStore.cityCode)
  } catch (cause) {
    ElMessage.error(errorMessage(cause))
  }
}

// ==================== 自动规则 ====================

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

function openCreate() { createDialogs.value?.openCreate() }

function openBatch() { createDialogs.value?.openBatch() }

function openCompletion(task: OperationsTask) { evidenceDialogs.value?.openCompletion(task) }

function openException(task: OperationsTask) { evidenceDialogs.value?.openException(task) }

/** 输入: 已变更的任务编号; 输出: 刷新队列, 并在详情已打开时同步详情. */
async function handleEvidenceChanged(taskId: string) {
  await loadTasks()
  if (detailVisible.value && detail.value?.task.taskId === taskId) await openDetail(taskId)
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

    <OperationsTaskCreateDialogs
      ref="createDialogs" :city-code="appStore.cityCode" :default-org-id="defaultOrgId"
      :is-admin="isAdmin" @changed="loadTasks"
    />

    <OperationsTaskEvidenceDialogs ref="evidenceDialogs" @changed="handleEvidenceChanged" />

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
.heading-actions, .dialog-tools { display: flex; align-items: center; gap: 8px; }
.heading-actions { flex-wrap: wrap; justify-content: flex-end; }
.task-form { display: grid; grid-template-columns: 1fr 1fr; gap: 0 16px; } .task-form .wide { grid-column: 1 / -1; }
.task-form :deep(.el-select), .task-form :deep(.el-date-editor) { width: 100%; }
.full-width { width: 100%; } .dialog-tools { justify-content: flex-end; margin-bottom: 12px; }
.route-summary { display: flex; align-items: baseline; gap: 12px; padding: 16px 2px 12px; } .route-summary strong { font-size: 24px; } .route-summary span { color: var(--muted); font-size: 12px; }
</style>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { reactive, ref } from 'vue'

import {
  createOperationsBatch, createOperationsTask, getOperationsAssignees,
} from '@/api/operations'
import { errorMessage } from '@/api/http'
import { getVehicles } from '@/api/vehicle'
import type {
  OperationsAssignee, OperationsBatchTaskRequest, OperationsTaskRequest,
} from '@/types/operations'
import type { VehicleListItem } from '@/types/vehicle'
import { taskPriorityLabels, taskTypeLabels } from '@/utils/operations'

const props = defineProps<{ cityCode: string; defaultOrgId: string; isAdmin: boolean }>()
const emit = defineEmits<{ changed: [] }>()

const createVisible = ref(false)
const batchVisible = ref(false)
const submitting = ref(false)
const assignees = ref<OperationsAssignee[]>([])
const vehicles = ref<VehicleListItem[]>([])
const createForm = reactive<OperationsTaskRequest>({
  taskType: 'BATTERY_SWAP', priority: 'NORMAL', title: '', description: null,
  vehicleId: '', orgId: '', targetName: null, dueAt: null, assigneeId: null,
})
const batchForm = reactive<OperationsBatchTaskRequest>({
  batchName: '', taskType: 'REBALANCE', priority: 'NORMAL', title: '', description: null,
  vehicleIds: [], orgId: '', targetName: null, dueAt: null, assigneeId: null,
})

/** 输入: 车辆搜索关键字; 输出: 当前城市最多 20 辆候选车辆. */
async function searchVehicles(keyword = '') {
  try {
    const result = await getVehicles({
      cityCode: props.cityCode, keyword: keyword.trim() || undefined, page: 1, pageSize: 20,
    })
    vehicles.value = result.items
  } catch (cause) {
    ElMessage.error(errorMessage(cause))
  }
}

async function loadAssignees() {
  try {
    assignees.value = await getOperationsAssignees(props.cityCode)
  } catch (cause) {
    ElMessage.error(errorMessage(cause))
  }
}

/** 输入: 无; 输出: 重置并打开单任务创建表单. */
async function openCreate() {
  Object.assign(createForm, {
    taskType: 'BATTERY_SWAP', priority: 'NORMAL', title: '', description: null,
    vehicleId: '', orgId: props.defaultOrgId, targetName: null, dueAt: null, assigneeId: null,
  })
  await Promise.all([searchVehicles(), loadAssignees()])
  createVisible.value = true
}

/** 输入: 无; 输出: 重置并打开批量建单表单. */
async function openBatch() {
  Object.assign(batchForm, {
    batchName: '', taskType: 'REBALANCE', priority: 'NORMAL', title: '', description: null,
    vehicleIds: [], orgId: props.defaultOrgId, targetName: null, dueAt: null, assigneeId: null,
  })
  await Promise.all([searchVehicles(), loadAssignees()])
  batchVisible.value = true
}

/** 输入: 新任务表单; 输出: 创建任务并通知父页面刷新. */
async function submitCreate() {
  if (!createForm.title.trim() || !createForm.vehicleId || !createForm.orgId) {
    ElMessage.warning('请填写任务标题并选择车辆')
    return
  }
  submitting.value = true
  try {
    await createOperationsTask({
      ...createForm, title: createForm.title.trim(),
      description: createForm.description?.trim() || null,
      targetName: createForm.targetName?.trim() || null,
      assigneeId: props.isAdmin ? createForm.assigneeId : null,
    })
    ElMessage.success('运维任务已创建')
    createVisible.value = false
    emit('changed')
  } catch (cause) {
    ElMessage.error(errorMessage(cause))
  } finally {
    submitting.value = false
  }
}

/** 输入: 批量任务模板; 输出: 部分成功结果并通知父页面刷新. */
async function submitBatch() {
  if (!batchForm.batchName.trim() || !batchForm.title.trim() || batchForm.vehicleIds.length === 0) {
    ElMessage.warning('请填写批次名称、任务标题并选择车辆')
    return
  }
  submitting.value = true
  try {
    const result = await createOperationsBatch({
      ...batchForm, batchName: batchForm.batchName.trim(), title: batchForm.title.trim(),
      description: batchForm.description?.trim() || null,
      assigneeId: props.isAdmin ? batchForm.assigneeId : null,
    })
    ElMessage.success(`批次 ${result.batchNo} 创建 ${result.createdTasks.length} 项, 跳过 ${result.skipped.length} 项`)
    batchVisible.value = false
    emit('changed')
  } catch (cause) {
    ElMessage.error(errorMessage(cause))
  } finally {
    submitting.value = false
  }
}

function batteryText(value: number | null) { return value === null ? '--' : `${value}%` }

defineExpose({ openCreate, openBatch })
</script>

<template>
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
    <template #footer><el-button @click="createVisible = false">取消</el-button><el-button type="primary" :loading="submitting" @click="submitCreate">创建任务</el-button></template>
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
    <template #footer><el-button @click="batchVisible = false">取消</el-button><el-button type="primary" :loading="submitting" @click="submitBatch">创建批次</el-button></template>
  </el-dialog>
</template>

<style scoped>
.task-form { display: grid; grid-template-columns: 1fr 1fr; gap: 0 16px; }
.task-form .wide { grid-column: 1 / -1; }
.task-form :deep(.el-select), .task-form :deep(.el-date-editor) { width: 100%; }
</style>

<script setup lang="ts">
import { Location, UploadFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { UploadRequestOptions } from 'element-plus'
import { computed, reactive, ref } from 'vue'

import {
  completeOperationsTask, reportOperationsException, uploadOperationsAttachment,
} from '@/api/operations'
import { errorMessage } from '@/api/http'
import type {
  OperationsAttachment, OperationsCompletionRequest, OperationsExceptionType,
  OperationsTask, OperationsTaskType,
} from '@/types/operations'
import { exceptionTypeLabels } from '@/utils/operations'

const emit = defineEmits<{ changed: [taskId: string] }>()

const selectedTask = ref<OperationsTask | null>(null)
const completionVisible = ref(false)
const exceptionVisible = ref(false)
const submitting = ref(false)
const partsText = ref('')
const completionFiles = reactive<{ before: OperationsAttachment[]; after: OperationsAttachment[] }>({
  before: [], after: [],
})
const exceptionFiles = ref<OperationsAttachment[]>([])
const completionForm = reactive<OperationsCompletionRequest>({
  resultNote: '', arrivalLongitude: 0, arrivalLatitude: 0, coordinateSystem: 'WGS84', checklist: [],
  removedBatteryId: null, installedBatteryId: null, partsUsed: [],
  targetLongitude: null, targetLatitude: null, beforeAttachmentIds: [], afterAttachmentIds: [],
})
const exceptionForm = reactive<{ exceptionType: OperationsExceptionType; note: string }>({
  exceptionType: 'VEHICLE_NOT_FOUND', note: '',
})
const completionCheckOptions = computed(() => ({
  BATTERY_SWAP: ['核对车辆编号', '检查电池仓与接头', '确认新电池锁定', '确认车辆恢复供电'],
  REBALANCE: ['核对车辆编号', '确认停车区域合规', '车辆摆放整齐', '未阻塞道路或出入口'],
  REPAIR: ['核对故障现象', '完成维修或部件更换', '完成安全检查', '车辆功能复测通过'],
  INSPECTION: ['检查车身与车锁', '检查刹车与轮胎', '检查电池状态', '记录异常项'],
  RETRIEVAL: ['核对车辆编号', '记录车辆现状', '确认装车固定', '现场无遗留物'],
  CLEANING: ['完成车身清洁', '完成车篮清洁', '检查二维码可识别', '车辆摆放合规'],
} as Record<OperationsTaskType, string[]>)[selectedTask.value?.taskType ?? 'INSPECTION'])

/** 输入: 当前任务; 输出: 初始化并打开结构化完工凭证表单. */
function openCompletion(task: OperationsTask) {
  selectedTask.value = task
  Object.assign(completionForm, {
    resultNote: '', arrivalLongitude: task.sourceLongitude ?? 0, arrivalLatitude: task.sourceLatitude ?? 0,
    coordinateSystem: 'WGS84', checklist: [], removedBatteryId: null, installedBatteryId: null, partsUsed: [],
    targetLongitude: null, targetLatitude: null, beforeAttachmentIds: [], afterAttachmentIds: [],
  })
  completionFiles.before = []
  completionFiles.after = []
  partsText.value = ''
  completionVisible.value = true
}

/** 输入: 可上报任务; 输出: 初始化并打开现场异常表单. */
function openException(task: OperationsTask) {
  selectedTask.value = task
  exceptionForm.exceptionType = 'VEHICLE_NOT_FOUND'
  exceptionForm.note = ''
  exceptionFiles.value = []
  exceptionVisible.value = true
}

/** 输入: 浏览器定位权限; 输出: WGS84 现场坐标, 失败时保留当前表单值. */
function locateForEvidence() {
  if (!navigator.geolocation) {
    ElMessage.warning('当前浏览器不支持定位')
    return
  }
  navigator.geolocation.getCurrentPosition((position) => {
    completionForm.arrivalLongitude = Number(position.coords.longitude.toFixed(7))
    completionForm.arrivalLatitude = Number(position.coords.latitude.toFixed(7))
    ElMessage.success('已读取现场位置')
  }, () => ElMessage.error('无法读取现场位置, 请检查浏览器定位权限'))
}

/** 输入: 上传参数、附件用途和展示列表; 输出: 服务端登记后的附件元数据. */
async function uploadEvidence(
  options: UploadRequestOptions,
  purpose: 'BEFORE' | 'AFTER' | 'EXCEPTION',
  target: OperationsAttachment[],
) {
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

/** 输入: 已校验的完工表单; 输出: 任务进入管理员验收并通知父页面刷新. */
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
  submitting.value = true
  try {
    await completeOperationsTask(task.taskId, { ...completionForm })
    ElMessage.success('提交完工成功')
    completionVisible.value = false
    emit('changed', task.taskId)
  } catch (cause) {
    ElMessage.error(errorMessage(cause))
  } finally {
    submitting.value = false
  }
}

/** 输入: 异常类型、说明和照片; 输出: 任务进入异常待处理状态并通知父页面刷新. */
async function submitException() {
  const task = selectedTask.value
  if (!task || !exceptionForm.note.trim()) {
    ElMessage.warning('请填写现场异常说明')
    return
  }
  submitting.value = true
  try {
    await reportOperationsException(task.taskId, {
      ...exceptionForm, note: exceptionForm.note.trim(),
      attachmentIds: exceptionFiles.value.map(item => item.attachmentId),
    })
    ElMessage.success('上报异常成功')
    exceptionVisible.value = false
    emit('changed', task.taskId)
  } catch (cause) {
    ElMessage.error(errorMessage(cause))
  } finally {
    submitting.value = false
  }
}

defineExpose({ openCompletion, openException })
</script>

<template>
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
    <template #footer><el-button @click="completionVisible = false">取消</el-button><el-button type="primary" :loading="submitting" @click="submitCompletion">提交验收</el-button></template>
  </el-dialog>

  <el-dialog v-model="exceptionVisible" title="上报现场异常" width="560px" destroy-on-close>
    <el-form label-position="top">
      <el-form-item label="异常类型"><el-select v-model="exceptionForm.exceptionType" class="full-width"><el-option v-for="(label, value) in exceptionTypeLabels" :key="value" :label="label" :value="value" /></el-select></el-form-item>
      <el-form-item label="现场说明"><el-input v-model="exceptionForm.note" type="textarea" :rows="4" maxlength="500" show-word-limit /></el-form-item>
      <el-form-item label="现场照片"><div class="upload-row"><el-upload :http-request="uploadException" :show-file-list="false" accept="image/jpeg,image/png"><el-button :icon="UploadFilled">上传照片</el-button></el-upload><a v-for="file in exceptionFiles" :key="file.attachmentId" :href="file.downloadUrl" target="_blank">{{ file.originalName }}</a></div></el-form-item>
    </el-form>
    <template #footer><el-button @click="exceptionVisible = false">取消</el-button><el-button type="danger" :loading="submitting" @click="submitException">确认上报</el-button></template>
  </el-dialog>
</template>

<style scoped>
.evidence-form { display: grid; grid-template-columns: 1fr 1fr; gap: 0 16px; }
.evidence-form .wide { grid-column: 1 / -1; }
.evidence-form :deep(.el-input-number), .full-width { width: 100%; }
.location-field, .upload-row { display: flex; align-items: center; gap: 8px; }
.check-grid { display: grid; grid-template-columns: 1fr 1fr; width: 100%; }
.upload-row { flex-wrap: wrap; }
.upload-row a { color: #1769a8; font-size: 12px; text-decoration: none; }
</style>

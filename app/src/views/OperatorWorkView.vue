<script setup lang="ts">
import { showConfirmDialog, showToast } from 'vant'
import { computed, onMounted, ref, watch } from 'vue'

import { completeTask, errorText, getTasks, reportException, taskAction, uploadAttachment } from '@/api'
import { requestLocation } from '@/bridge'
import TaskCard from '@/components/TaskCard.vue'
import { useAppStore } from '@/stores/app'
import type { ExceptionType, Task } from '@/types'
import { exceptionLabels } from '@/utils'

const app = useAppStore()
const tasks = ref<Task[]>([])
const activeTask = ref<Task | null>(null)
const completionVisible = ref(false)
const exceptionVisible = ref(false)
const workingId = ref('')
const uploading = ref(false)
const resultNote = ref('现场作业已按要求完成')
const location = ref<{ longitude: number; latitude: number } | null>(null)
const checklist = ref<string[]>([])
const beforeAttachmentIds = ref<number[]>([])
const afterAttachmentIds = ref<number[]>([])
const beforeNames = ref<string[]>([])
const afterNames = ref<string[]>([])
const removedBatteryId = ref('')
const installedBatteryId = ref('')
const targetLongitude = ref<number | undefined>()
const targetLatitude = ref<number | undefined>()
const exceptionType = ref<ExceptionType>('VEHICLE_NOT_FOUND')
const exceptionNote = ref('')
const exceptionAttachmentIds = ref<number[]>([])
const exceptionNames = ref<string[]>([])

const exceptionOptions = Object.entries(exceptionLabels).map(([value, text]) => ({ value, text }))
const checklistOptions = computed(() => {
  switch (activeTask.value?.taskType) {
    case 'BATTERY_SWAP': return ['核对车辆编号', '记录旧电池编号', '确认新电池锁定', '确认车辆恢复在线']
    case 'REBALANCE': return ['核对车辆编号', '确认目标停车点', '车辆摆放整齐', '确认定位已更新']
    case 'REPAIR': return ['故障复检', '维修过程留痕', '功能测试通过', '清理作业现场']
    default: return ['核对车辆编号', '完成规定作业', '检查车辆状态', '清理作业现场']
  }
})

/** 输入: 当前城市和登录人员; 输出: 该人员所有未闭环作业。 */
async function loadWork() {
  try {
    const result = await getTasks({ cityCode: app.cityCode, scope: 'MINE' })
    tasks.value = result.items.filter((task) => ['CLAIMED', 'IN_PROGRESS', 'EXCEPTION', 'PENDING_REVIEW'].includes(task.status))
  } catch (error) {
    showToast(errorText(error))
  }
}

/** 输入: 已领取任务与 start/release 动作; 输出: 更新后的任务状态。 */
async function transition(task: Task, action: 'start' | 'release') {
  try {
    if (action === 'release') await showConfirmDialog({ title: '释放任务', message: '释放后任务将返回公共任务池。' })
    workingId.value = task.taskId
    await taskAction(task.taskId, action)
    showToast(action === 'start' ? '已开始作业' : '任务已释放')
    await loadWork()
  } catch (error) {
    if (error !== 'cancel') showToast(errorText(error))
  } finally {
    workingId.value = ''
  }
}

function resetEvidence(task: Task) {
  activeTask.value = task
  resultNote.value = '现场作业已按要求完成'
  location.value = task.sourceLongitude !== null && task.sourceLatitude !== null
    ? { longitude: task.sourceLongitude, latitude: task.sourceLatitude } : null
  checklist.value = [...checklistOptions.value]
  beforeAttachmentIds.value = []
  afterAttachmentIds.value = []
  beforeNames.value = []
  afterNames.value = []
  removedBatteryId.value = ''
  installedBatteryId.value = ''
  targetLongitude.value = undefined
  targetLatitude.value = undefined
}

function openCompletion(task: Task) {
  resetEvidence(task)
  completionVisible.value = true
}

function openException(task: Task) {
  activeTask.value = task
  exceptionType.value = 'VEHICLE_NOT_FOUND'
  exceptionNote.value = ''
  exceptionAttachmentIds.value = []
  exceptionNames.value = []
  exceptionVisible.value = true
}

async function locate() {
  try {
    location.value = await requestLocation()
    showToast('已记录当前位置')
  } catch (error) {
    showToast(errorText(error))
  }
}

/** 输入: 相机或相册中的图片及凭证用途; 输出: 服务端附件编号，供完工或异常提交引用。 */
async function upload(event: Event, purpose: 'BEFORE' | 'AFTER' | 'EXCEPTION') {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file || !activeTask.value) return
  uploading.value = true
  try {
    const attachment = await uploadAttachment(activeTask.value.taskId, purpose, file)
    if (purpose === 'BEFORE') {
      beforeAttachmentIds.value.push(attachment.attachmentId)
      beforeNames.value.push(file.name)
    } else if (purpose === 'AFTER') {
      afterAttachmentIds.value.push(attachment.attachmentId)
      afterNames.value.push(file.name)
    } else {
      exceptionAttachmentIds.value.push(attachment.attachmentId)
      exceptionNames.value.push(file.name)
    }
    showToast('凭证已上传')
  } catch (error) {
    showToast(errorText(error))
  } finally {
    uploading.value = false
    ;(event.target as HTMLInputElement).value = ''
  }
}

/** 输入: 定位、检查项、照片和专项作业数据; 输出: 提交管理员验收的完整作业凭证。 */
async function submitCompletion() {
  if (!activeTask.value || !location.value) return showToast('请先记录到场位置')
  if (checklist.value.length !== checklistOptions.value.length) return showToast('请完成全部检查项')
  if (!afterAttachmentIds.value.length) return showToast('请至少上传一张完工照片')
  if (activeTask.value.taskType === 'BATTERY_SWAP' && (!removedBatteryId.value || !installedBatteryId.value)) return showToast('请填写新旧电池编号')
  if (activeTask.value.taskType === 'REBALANCE' && (targetLongitude.value === undefined || targetLatitude.value === undefined)) return showToast('请填写目标停车点坐标')
  try {
    workingId.value = activeTask.value.taskId
    await completeTask(activeTask.value.taskId, {
      resultNote: resultNote.value,
      arrivalLongitude: location.value.longitude,
      arrivalLatitude: location.value.latitude,
      checklist: checklist.value,
      removedBatteryId: removedBatteryId.value || null,
      installedBatteryId: installedBatteryId.value || null,
      partsUsed: [],
      targetLongitude: targetLongitude.value ?? null,
      targetLatitude: targetLatitude.value ?? null,
      beforeAttachmentIds: beforeAttachmentIds.value,
      afterAttachmentIds: afterAttachmentIds.value,
    })
    completionVisible.value = false
    showToast('作业凭证已提交验收')
    await loadWork()
  } catch (error) {
    showToast(errorText(error))
  } finally {
    workingId.value = ''
  }
}

/** 输入: 异常分类、说明和现场照片; 输出: 将任务转入管理员异常闭环。 */
async function submitException() {
  if (!activeTask.value || !exceptionNote.value.trim()) return showToast('请填写异常说明')
  try {
    workingId.value = activeTask.value.taskId
    await reportException(activeTask.value.taskId, exceptionType.value, exceptionNote.value, exceptionAttachmentIds.value)
    exceptionVisible.value = false
    showToast('异常已上报')
    await loadWork()
  } catch (error) {
    showToast(errorText(error))
  } finally {
    workingId.value = ''
  }
}

onMounted(loadWork)
watch(() => app.cityCode, loadWork)
</script>

<template>
  <div>
    <div class="section-head"><h2>我的作业</h2><span>{{ tasks.length }} 项待闭环</span></div>
    <div v-if="tasks.length" class="task-list">
      <TaskCard v-for="task in tasks" :key="task.taskId" :task="task">
        <template v-if="task.status === 'CLAIMED'">
          <van-button size="small" plain :loading="workingId === task.taskId" @click="transition(task, 'release')">释放</van-button>
          <van-button size="small" type="primary" :loading="workingId === task.taskId" :data-test="`start-${task.taskId}`" @click="transition(task, 'start')">开始作业</van-button>
        </template>
        <template v-if="task.status === 'IN_PROGRESS'">
          <van-button size="small" plain type="danger" :data-test="`exception-${task.taskId}`" @click="openException(task)">上报异常</van-button>
          <van-button size="small" type="primary" :data-test="`complete-${task.taskId}`" @click="openCompletion(task)">完工作业</van-button>
        </template>
        <van-tag v-if="task.status === 'PENDING_REVIEW'" plain type="warning">等待管理员验收</van-tag>
      </TaskCard>
    </div>
    <div v-else class="empty-state">暂无待处理作业</div>

    <van-popup v-model:show="completionVisible" position="bottom" class="work-sheet">
      <div class="sheet-head"><h3>提交作业凭证</h3><span>{{ activeTask?.vehicleId }}</span></div>
      <div class="sheet-scroll">
        <div class="form-block">
          <van-field v-model="resultNote" label="作业结果" type="textarea" rows="2" required />
          <van-cell title="到场位置" :value="location ? `${location.longitude.toFixed(5)}, ${location.latitude.toFixed(5)}` : '尚未定位'">
            <template #right-icon><van-button size="mini" plain type="primary" data-test="locate-button" @click="locate"><van-icon name="location-o" /> 定位</van-button></template>
          </van-cell>
        </div>
        <div class="form-block">
          <div class="form-title">检查项</div>
          <van-checkbox-group v-model="checklist">
            <van-cell v-for="item in checklistOptions" :key="item" clickable :title="item" @click="checklist.includes(item) ? checklist = checklist.filter((value) => value !== item) : checklist.push(item)">
              <template #right-icon><van-checkbox :name="item" @click.stop /></template>
            </van-cell>
          </van-checkbox-group>
        </div>
        <div v-if="activeTask?.taskType === 'BATTERY_SWAP'" class="form-block">
          <van-field v-model="removedBatteryId" label="拆下电池" required />
          <van-field v-model="installedBatteryId" label="装入电池" required />
        </div>
        <div v-if="activeTask?.taskType === 'REBALANCE'" class="form-block">
          <van-field v-model.number="targetLongitude" label="目标经度" type="number" required />
          <van-field v-model.number="targetLatitude" label="目标纬度" type="number" required />
        </div>
        <div class="evidence-grid">
          <label class="evidence-upload"><van-icon name="photograph" size="24" /><strong>作业前照片</strong><span>{{ beforeNames.length ? `${beforeNames.length} 张` : '选填' }}</span><input type="file" accept="image/*" capture="environment" data-test="before-photo" @change="upload($event, 'BEFORE')"></label>
          <label class="evidence-upload required"><van-icon name="photograph" size="24" /><strong>完工照片</strong><span>{{ afterNames.length ? `${afterNames.length} 张` : '至少 1 张' }}</span><input type="file" accept="image/*" capture="environment" data-test="after-photo" @change="upload($event, 'AFTER')"></label>
        </div>
      </div>
      <div class="sheet-actions"><van-button block type="primary" :loading="uploading || workingId === activeTask?.taskId" data-test="completion-submit" @click="submitCompletion">提交验收</van-button></div>
    </van-popup>

    <van-popup v-model:show="exceptionVisible" position="bottom" class="work-sheet compact">
      <div class="sheet-head"><h3><van-icon name="warning-o" /> 上报异常</h3><span>{{ activeTask?.taskNo }}</span></div>
      <div class="sheet-scroll">
        <div class="form-block">
          <van-field v-model="exceptionType" label="异常类型" is-link readonly>
            <template #input><select v-model="exceptionType" class="plain-select"><option v-for="option in exceptionOptions" :key="option.value" :value="option.value">{{ option.text }}</option></select></template>
          </van-field>
          <van-field v-model="exceptionNote" label="异常说明" type="textarea" rows="3" required data-test="exception-note" />
        </div>
        <label class="evidence-upload full"><van-icon name="photograph" size="24" /><strong>现场照片</strong><span>{{ exceptionNames.length ? `${exceptionNames.length} 张` : '可选' }}</span><input type="file" accept="image/*" capture="environment" @change="upload($event, 'EXCEPTION')"></label>
      </div>
      <div class="sheet-actions"><van-button block type="danger" :loading="uploading || workingId === activeTask?.taskId" data-test="exception-submit" @click="submitException">提交异常</van-button></div>
    </van-popup>
  </div>
</template>

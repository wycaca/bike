<script setup lang="ts">
import { showConfirmDialog, showToast } from 'vant'
import { computed, onMounted, ref, watch } from 'vue'

import { createBatch, errorText, getRules, getTasks, resolveException, reviewTask, scanRules, updateRule } from '@/api'
import TaskCard from '@/components/TaskCard.vue'
import { useAppStore } from '@/stores/app'
import { useAuthStore } from '@/stores/auth'
import type { BatchCreateResult, Task, TaskPriority, TaskRule, TaskType } from '@/types'
import { taskTypeLabels } from '@/utils'

const app = useAppStore()
const auth = useAuthStore()
const activeTab = ref('review')
const reviewTasks = ref<Task[]>([])
const exceptionTasks = ref<Task[]>([])
const rules = ref<TaskRule[]>([])
const batchVehicles = ref('')
const batchTitle = ref('批量运维任务')
const batchName = ref(`移动端批次-${new Date().toLocaleDateString('zh-CN')}`)
const batchType = ref<TaskType>('BATTERY_SWAP')
const batchPriority = ref<TaskPriority>('NORMAL')
const batchResult = ref<BatchCreateResult | null>(null)
const submitting = ref(false)

const taskTypeOptions = Object.entries(taskTypeLabels).map(([value, text]) => ({ value, text }))
const vehicleIds = computed(() => [...new Set(batchVehicles.value.split(/[\s,，]+/).map((item) => item.trim()).filter(Boolean))])

/** 输入: 当前城市; 输出: 验收、异常和自动规则三个管理队列。 */
async function loadControlData() {
  try {
    const [reviewResult, exceptionResult, ruleResult] = await Promise.all([
      getTasks({ cityCode: app.cityCode, scope: 'ALL', status: 'PENDING_REVIEW' }),
      getTasks({ cityCode: app.cityCode, scope: 'ALL', status: 'EXCEPTION' }),
      getRules(app.cityCode),
    ])
    reviewTasks.value = reviewResult.items
    exceptionTasks.value = exceptionResult.items
    rules.value = ruleResult
  } catch (error) {
    showToast(errorText(error))
  }
}

/** 输入: 待验收任务及结论; 输出: 完成验收或退回作业人员补充。 */
async function review(task: Task, action: 'APPROVE' | 'REJECT') {
  try {
    await showConfirmDialog({
      title: action === 'APPROVE' ? '通过验收' : '退回任务',
      message: action === 'APPROVE' ? `确认 ${task.taskNo} 作业凭证合格？` : `确认退回 ${task.taskNo} 重新作业？`,
    })
    await reviewTask(task.taskId, action, action === 'APPROVE' ? '移动端验收通过' : '移动端验收退回')
    showToast(action === 'APPROVE' ? '验收已通过' : '任务已退回')
    await loadControlData()
  } catch (error) {
    if (error !== 'cancel') showToast(errorText(error))
  }
}

/** 输入: 异常任务与处理方式; 输出: 重新开放任务或关闭异常任务。 */
async function handleException(task: Task, action: 'REOPEN' | 'CLOSE') {
  try {
    await showConfirmDialog({ title: '处理异常', message: action === 'REOPEN' ? '重新开放任务供运维人员领取？' : '确认关闭此异常任务？' })
    await resolveException(task.taskId, action, action === 'REOPEN' ? '管理员重新开放' : '管理员确认关闭')
    showToast('异常已处理')
    await loadControlData()
  } catch (error) {
    if (error !== 'cancel') showToast(errorText(error))
  }
}

/** 输入: 自动任务规则及新的启停值; 输出: 更新规则版本并替换本地数据。 */
async function toggleRule(rule: TaskRule, enabled: boolean) {
  try {
    const updated = await updateRule({ ...rule, enabled })
    rules.value = rules.value.map((item) => item.ruleId === updated.ruleId ? updated : item)
    showToast(enabled ? '规则已启用' : '规则已停用')
  } catch (error) {
    showToast(errorText(error))
  }
}

async function triggerScan() {
  try {
    const result = await scanRules(app.cityCode)
    showToast(`扫描 ${result.scannedVehicles} 辆，新增 ${result.createdTasks} 项`)
    await loadControlData()
  } catch (error) {
    showToast(errorText(error))
  }
}

/** 输入: 批次名称、任务模板和车辆列表; 输出: 创建结果及去重跳过明细。 */
async function submitBatch() {
  if (!vehicleIds.value.length) return showToast('请至少填写一辆车')
  submitting.value = true
  try {
    batchResult.value = await createBatch({
      batchName: batchName.value, taskType: batchType.value, priority: batchPriority.value,
      title: batchTitle.value, description: '由安卓端管理工作台批量创建',
      vehicleIds: vehicleIds.value, orgId: auth.user?.orgId || '',
    })
    showToast(`已创建 ${batchResult.value.createdTasks.length} 项任务`)
  } catch (error) {
    showToast(errorText(error))
  } finally {
    submitting.value = false
  }
}

onMounted(loadControlData)
watch(() => app.cityCode, loadControlData)
</script>

<template>
  <div>
    <van-tabs v-model:active="activeTab" sticky offset-top="76">
      <van-tab title="验收" name="review">
        <div class="tab-body">
          <TaskCard v-for="task in reviewTasks" :key="task.taskId" :task="task">
            <van-button size="small" plain type="danger" :data-test="`reject-${task.taskId}`" @click="review(task, 'REJECT')">退回</van-button>
            <van-button size="small" type="primary" :data-test="`approve-${task.taskId}`" @click="review(task, 'APPROVE')">通过</van-button>
          </TaskCard>
          <div v-if="!reviewTasks.length" class="empty-state">没有待验收任务</div>
        </div>
      </van-tab>
      <van-tab title="异常" name="exception">
        <div class="tab-body">
          <TaskCard v-for="task in exceptionTasks" :key="task.taskId" :task="task">
            <van-button size="small" plain type="primary" @click="handleException(task, 'REOPEN')">重新开放</van-button>
            <van-button size="small" type="danger" @click="handleException(task, 'CLOSE')">关闭</van-button>
          </TaskCard>
          <div v-if="!exceptionTasks.length" class="empty-state">没有待处理异常</div>
        </div>
      </van-tab>
      <van-tab title="规则" name="rules">
        <div class="tab-body">
          <van-button block plain type="primary" data-test="scan-rules" @click="triggerScan">立即执行规则扫描</van-button>
          <article v-for="rule in rules" :key="rule.ruleId" class="rule-row">
            <div><strong>{{ rule.ruleName }}</strong><span>{{ taskTypeLabels[rule.taskType] }} · 冷却 {{ rule.cooldownMinutes }} 分钟</span></div>
            <van-switch :model-value="rule.enabled" size="22" :data-test="`rule-${rule.ruleId}`" @update:model-value="toggleRule(rule, $event)" />
          </article>
        </div>
      </van-tab>
      <van-tab title="批量" name="batch">
        <div class="tab-body">
          <div class="form-block">
            <van-field v-model="batchName" label="批次名称" required />
            <van-field v-model="batchTitle" label="任务标题" required />
            <van-field v-model="batchType" label="任务类型" is-link readonly>
              <template #input>
                <select v-model="batchType" class="plain-select"><option v-for="option in taskTypeOptions" :key="option.value" :value="option.value">{{ option.text }}</option></select>
              </template>
            </van-field>
            <van-field v-model="batchVehicles" rows="5" autosize type="textarea" label="车辆编号" placeholder="使用逗号、空格或换行分隔，最多 200 辆" data-test="batch-vehicles" />
          </div>
          <van-button block type="primary" :loading="submitting" data-test="batch-submit" @click="submitBatch">创建 {{ vehicleIds.length }} 项批量任务</van-button>
          <div v-if="batchResult" class="batch-result">
            <strong>{{ batchResult.batchNo }}</strong>
            <span>成功 {{ batchResult.createdTasks.length }} 项，跳过 {{ batchResult.skipped.length }} 项</span>
          </div>
        </div>
      </van-tab>
    </van-tabs>
  </div>
</template>

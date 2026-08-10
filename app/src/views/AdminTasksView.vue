<script setup lang="ts">
import { showToast } from 'vant'
import { onMounted, ref, watch } from 'vue'

import { assignTask, errorText, getAssignees, getTasks } from '@/api'
import TaskCard from '@/components/TaskCard.vue'
import { useAppStore } from '@/stores/app'
import type { Assignee, Task, TaskStatus } from '@/types'

const app = useAppStore()
const status = ref<TaskStatus | ''>('')
const keyword = ref('')
const tasks = ref<Task[]>([])
const assignees = ref<Assignee[]>([])
const selectedTask = ref<Task | null>(null)
const selectedAssignee = ref('')
const assignmentVisible = ref(false)
const loading = ref(false)

const statusOptions: Array<{ text: string; value: TaskStatus | '' }> = [
  { text: '全部状态', value: '' }, { text: '待领取', value: 'OPEN' },
  { text: '已领取', value: 'CLAIMED' }, { text: '执行中', value: 'IN_PROGRESS' },
  { text: '待验收', value: 'PENDING_REVIEW' }, { text: '异常', value: 'EXCEPTION' },
  { text: '已完成', value: 'COMPLETED' },
]

/** 输入: 城市、状态与关键字; 输出: 管理员可见的任务列表。 */
async function loadTasks() {
  loading.value = true
  try {
    const result = await getTasks({ cityCode: app.cityCode, scope: 'ALL', status: status.value || undefined, keyword: keyword.value || undefined })
    tasks.value = result.items
  } catch (error) {
    showToast(errorText(error))
  } finally {
    loading.value = false
  }
}

/** 输入: 待指派任务; 输出: 打开当前城市人员选择面板。 */
async function openAssignment(task: Task) {
  selectedTask.value = task
  selectedAssignee.value = task.assigneeId || ''
  try {
    assignees.value = await getAssignees(app.cityCode)
    assignmentVisible.value = true
  } catch (error) {
    showToast(errorText(error))
  }
}

/** 输入: 任务与选中的人员; 输出: 保存指派关系并刷新列表。 */
async function submitAssignment() {
  if (!selectedTask.value || !selectedAssignee.value) return showToast('请选择运维人员')
  try {
    await assignTask(selectedTask.value.taskId, selectedAssignee.value)
    assignmentVisible.value = false
    showToast('任务已指派')
    await loadTasks()
  } catch (error) {
    showToast(errorText(error))
  }
}

onMounted(loadTasks)
watch(() => app.cityCode, loadTasks)
</script>

<template>
  <div>
    <div class="filter-row">
      <van-field v-model="keyword" clearable placeholder="任务号、车辆或人员" data-test="task-keyword" @keyup.enter="loadTasks" />
      <van-dropdown-menu>
        <van-dropdown-item v-model="status" :options="statusOptions" @change="loadTasks" />
      </van-dropdown-menu>
    </div>
    <div class="section-head"><h2>全部任务</h2><span>{{ tasks.length }} 项</span></div>
    <div v-if="tasks.length" class="task-list">
      <TaskCard v-for="task in tasks" :key="task.taskId" :task="task">
        <van-button v-if="!['COMPLETED', 'CANCELLED', 'PENDING_REVIEW'].includes(task.status)" size="small" plain type="primary" :data-test="`assign-${task.taskId}`" @click="openAssignment(task)">
          {{ task.assigneeId ? '改派' : '派单' }}
        </van-button>
      </TaskCard>
    </div>
    <van-loading v-else-if="loading" class="center-loading" />
    <div v-else class="empty-state">没有符合条件的任务</div>

    <van-popup v-model:show="assignmentVisible" position="bottom" round class="action-sheet">
      <div class="sheet-head"><h3>指派运维人员</h3><span>{{ selectedTask?.taskNo }}</span></div>
      <van-radio-group v-model="selectedAssignee" data-test="assignee-list">
        <van-cell-group inset>
          <van-cell v-for="person in assignees" :key="person.userId" clickable :title="person.displayName" :label="person.orgName" @click="selectedAssignee = person.userId">
            <template #right-icon><van-radio :name="person.userId" /></template>
          </van-cell>
        </van-cell-group>
      </van-radio-group>
      <div class="sheet-actions"><van-button block type="primary" data-test="assign-submit" @click="submitAssignment">确认指派</van-button></div>
    </van-popup>
  </div>
</template>

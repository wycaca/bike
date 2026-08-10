import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

import * as api from '@/api'
import type { Task } from '@/types'
import OperatorPoolView from '@/views/OperatorPoolView.vue'

vi.mock('@/api', () => ({
  getTasks: vi.fn(), taskAction: vi.fn(), errorText: (error: unknown) => String(error),
}))

const openTask: Task = {
  taskId: 'task-open-1', taskNo: 'OPS-001', taskType: 'BATTERY_SWAP', status: 'OPEN',
  priority: 'HIGH', sourceType: 'RULE', title: '低电量换电', description: null,
  vehicleId: 'BIKE-001', orgName: '北京运营中心', sourceLongitude: 116.39,
  sourceLatitude: 39.9, batteryPercent: 12, assigneeId: null, assigneeName: null,
  dueAt: null, duplicateCount: 0, exceptionType: null, exceptionNote: null,
}

describe('运维任务池点击流程', () => {
  it('点击抢单后调用后端并从任务池移除', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    vi.mocked(api.getTasks).mockResolvedValue({ items: [openTask], total: 1, page: 1, pageSize: 100 })
    vi.mocked(api.taskAction).mockResolvedValue({ task: { ...openTask, status: 'CLAIMED' }, events: [], evidence: [], exceptions: [], triggers: [] })
    const wrapper = mount(OperatorPoolView, { global: { plugins: [pinia] } })
    await flushPromises()

    await wrapper.find('[data-test="claim-task-open-1"]').trigger('click')
    await flushPromises()

    expect(api.taskAction).toHaveBeenCalledWith('task-open-1', 'claim')
    expect(wrapper.findAll('[data-test="task-card"]')).toHaveLength(0)
  })
})

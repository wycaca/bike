import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

import * as api from '@/api'
import { seedTestCity } from '@/test/city'
import type { Task } from '@/types'
import AdminTasksView from '@/views/AdminTasksView.vue'

vi.mock('@/api', () => ({
  getTasks: vi.fn(), getAssignees: vi.fn(), assignTask: vi.fn(),
  errorText: (error: unknown) => String(error),
}))

const task: Task = {
  taskId: 'task-admin-1', taskNo: 'OPS-002', taskType: 'REPAIR', status: 'OPEN',
  priority: 'URGENT', sourceType: 'MANUAL', title: '车辆故障维修', description: null,
  vehicleId: 'BIKE-002', orgName: '北京运营中心', sourceLongitude: 116.4,
  sourceLatitude: 39.91, batteryPercent: 66, assigneeId: null, assigneeName: null,
  dueAt: null, duplicateCount: 0, exceptionType: null, exceptionNote: null,
}

describe('管理员派单点击流程', () => {
  it('选择运维人员后提交派单', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    seedTestCity()
    vi.mocked(api.getTasks).mockResolvedValue({ items: [task], total: 1, page: 1, pageSize: 100 })
    vi.mocked(api.getAssignees).mockResolvedValue([{ userId: 'operator-9', displayName: '王师傅', phone: null, orgId: 'ORG-BJ-001', orgName: '北京运营中心' }])
    vi.mocked(api.assignTask).mockResolvedValue({ task: { ...task, status: 'CLAIMED', assigneeId: 'operator-9', assigneeName: '王师傅' }, events: [], evidence: [], exceptions: [], triggers: [] })
    const wrapper = mount(AdminTasksView, { attachTo: document.body, global: { plugins: [pinia] } })
    await flushPromises()

    await wrapper.find('[data-test="assign-task-admin-1"]').trigger('click')
    await flushPromises()
    await wrapper.find('[data-test="assignee-list"] .van-cell').trigger('click')
    await wrapper.find('[data-test="assign-submit"]').trigger('click')
    await flushPromises()

    expect(api.assignTask).toHaveBeenCalledWith('task-admin-1', 'operator-9')
    wrapper.unmount()
  })
})

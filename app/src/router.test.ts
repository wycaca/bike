import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory } from 'vue-router'

import { createAppRouter } from '@/router'
import { useAuthStore } from '@/stores/auth'
import type { CurrentUser, UserRole } from '@/types'

function setUser(role: UserRole) {
  const auth = useAuthStore()
  auth.initialized = true
  auth.user = {
    userId: 'user-1', username: 'tester', displayName: '测试用户', orgId: 'ORG-BJ-001',
    orgName: '北京运营中心', role,
  } satisfies CurrentUser
}

describe('角色路由守卫', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('阻止运维人员访问管理页面', async () => {
    setUser('OPERATOR')
    const router = createAppRouter(createMemoryHistory())
    await router.push('/admin/control')
    expect(router.currentRoute.value.path).toBe('/operator/pool')
  })

  it('阻止管理人员访问运维作业页面', async () => {
    setUser('ADMIN')
    const router = createAppRouter(createMemoryHistory())
    await router.push('/operator/work')
    expect(router.currentRoute.value.path).toBe('/admin/overview')
  })
})

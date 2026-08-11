import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'

import { useAuthStore } from '@/stores/auth'
import type { CurrentUser, UserRole } from '@/types'
import LoginView from '@/views/LoginView.vue'

function user(role: UserRole): CurrentUser {
  return {
    userId: role === 'ADMIN' ? 'admin-1' : 'operator-1', username: role.toLowerCase(),
    displayName: role === 'ADMIN' ? '管理员' : '运维人员', orgId: 'ORG-BJ-001',
    orgName: '北京运营中心', role, dataScope: role === 'ADMIN' ? 'ALL' : 'ORG_ONLY',
  }
}

describe('登录角色分流', () => {
  it.each([
    ['ADMIN' as const, '/admin/overview'],
    ['OPERATOR' as const, '/operator/pool'],
  ])('点击登录后将 %s 用户送入对应工作台', async (role, expectedPath) => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/login', component: LoginView },
        { path: '/admin/overview', component: { template: '<div>管理员工作台</div>' } },
        { path: '/operator/pool', component: { template: '<div>运维任务池</div>' } },
      ],
    })
    await router.push('/login')
    await router.isReady()
    const auth = useAuthStore()
    vi.spyOn(auth, 'signIn').mockResolvedValue(user(role))
    const wrapper = mount(LoginView, { global: { plugins: [pinia, router] } })

    await wrapper.find('input[name="username"]').setValue(role.toLowerCase())
    await wrapper.find('input[name="password"]').setValue('Password123!')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(auth.signIn).toHaveBeenCalledWith(role.toLowerCase(), 'Password123!')
    expect(router.currentRoute.value.path).toBe(expectedPath)
  })
})

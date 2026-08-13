import { createRouter, createWebHistory, type RouterHistory } from 'vue-router'

import { useAuthStore } from '@/stores/auth'
import { useAppStore } from '@/stores/app'
import type { UserRole } from '@/types'

const LoginView = () => import('@/views/LoginView.vue')
const RoleShell = () => import('@/components/RoleShell.vue')

export function roleHome(role: UserRole) {
  if (role === 'ADMIN') return '/admin/overview'
  if (role === 'OPERATOR') return '/operator/pool'
  return '/unsupported-role'
}

export function createAppRouter(history: RouterHistory = createWebHistory()) {
  const router = createRouter({
    history,
    routes: [
      { path: '/login', component: LoginView, meta: { title: '登录' } },
      { path: '/unsupported-role', component: () => import('@/views/UnsupportedRoleView.vue'), meta: { role: 'AUDITOR', title: '请使用桌面端' } },
      { path: '/city-unavailable', component: () => import('@/views/CityUnavailableView.vue'), meta: { roles: ['ADMIN', 'OPERATOR'], skipCity: true, title: '城市不可用' } },
      { path: '/service-unavailable', component: () => import('@/views/ServiceUnavailableView.vue'), meta: { serviceError: true, skipCity: true, title: '服务不可用' } },
      {
        path: '/', component: RoleShell,
        children: [
          { path: 'admin/overview', component: () => import('@/views/AdminOverviewView.vue'), meta: { role: 'ADMIN', title: '运营总览' } },
          { path: 'admin/tasks', component: () => import('@/views/AdminTasksView.vue'), meta: { role: 'ADMIN', title: '任务管理' } },
          { path: 'admin/control', component: () => import('@/views/AdminControlView.vue'), meta: { role: 'ADMIN', title: '作业管控' } },
          { path: 'operator/pool', component: () => import('@/views/OperatorPoolView.vue'), meta: { role: 'OPERATOR', title: '任务池' } },
          { path: 'operator/work', component: () => import('@/views/OperatorWorkView.vue'), meta: { role: 'OPERATOR', title: '我的作业' } },
          { path: 'operator/route', component: () => import('@/views/OperatorRouteView.vue'), meta: { role: 'OPERATOR', title: '作业路线' } },
          { path: 'vehicles', component: () => import('@/views/VehicleView.vue'), meta: { roles: ['ADMIN', 'OPERATOR'], title: '车辆' } },
          { path: 'profile', component: () => import('@/views/ProfileView.vue'), meta: { roles: ['ADMIN', 'OPERATOR'], title: '我的' } },
        ],
      },
      { path: '/:pathMatch(.*)*', component: () => import('@/views/NotFoundView.vue'), meta: { skipCity: true, title: '页面不存在' } },
    ],
  })

  router.beforeEach(async (to) => {
    const auth = useAuthStore()
    const app = useAppStore()
    if (!auth.initialized) await auth.bootstrap()
    if (to.meta.serviceError) return true
    if (auth.restoreError) return { path: '/service-unavailable', query: { redirect: to.fullPath } }
    if (!auth.user && to.path === '/login') {
      app.resetCities()
      return true
    }
    if (!auth.user && to.path !== '/login') {
      app.resetCities()
      return '/login'
    }
    if (auth.user && to.path === '/login') return roleHome(auth.user.role)
    if (to.path === '/' && auth.user) return roleHome(auth.user.role)
    const required = to.meta.role as UserRole | undefined
    const allowed = to.meta.roles as UserRole[] | undefined
    if (auth.user && ((required && required !== auth.user.role) || (allowed && !allowed.includes(auth.user.role)))) {
      return roleHome(auth.user.role)
    }
    if (auth.user && auth.user.role !== 'AUDITOR' && !to.meta.skipCity) {
      try {
        await app.ensureCities()
      } catch {
        return { path: '/city-unavailable', query: { redirect: to.fullPath } }
      }
    }
    return true
  })
  return router
}

export const router = createAppRouter()

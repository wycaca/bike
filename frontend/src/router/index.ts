import { createRouter, createWebHistory } from 'vue-router'

import AppLayout from '@/layouts/AppLayout.vue'
import { useAuthStore } from '@/stores/auth'
import type { UserRole } from '@/types/operations'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/',
      component: AppLayout,
      children: [
        { path: '', redirect: '/dashboard' },
        {
          path: 'dashboard',
          name: 'dashboard',
          component: () => import('@/views/DashboardView.vue'),
        },
        {
          path: 'reports/revenue',
          name: 'revenue-report',
          component: () => import('@/views/RevenueReportView.vue'),
        },
        { path: 'map', name: 'map', component: () => import('@/views/MapView.vue') },
        {
          path: 'vehicles',
          name: 'vehicles',
          component: () => import('@/views/VehicleListView.vue'),
        },
        {
          path: 'vehicles/:vehicleId/trajectory',
          name: 'trajectory',
          component: () => import('@/views/TrajectoryView.vue'),
        },
        {
          path: 'geo',
          name: 'geo',
          component: () => import('@/views/GeoManagementView.vue'),
          meta: { roles: ['ADMIN', 'OPERATOR'] },
        },
        {
          path: 'admin',
          name: 'admin',
          component: () => import('@/views/AdminView.vue'),
          meta: { roles: ['ADMIN', 'AUDITOR'] },
        },
      ],
    },
  ],
})

router.beforeEach(async (to) => {
  const authStore = useAuthStore()
  if (!authStore.initialized) await authStore.restore()
  if (to.meta.public) return authStore.authenticated ? '/dashboard' : true
  if (!authStore.authenticated) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  const roles = to.meta.roles as UserRole[] | undefined
  if (roles && !authStore.hasRole(...roles)) return '/dashboard'
  return true
})

export default router

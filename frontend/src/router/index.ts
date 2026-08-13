import { createRouter, createWebHistory } from 'vue-router'

import AppLayout from '@/layouts/AppLayout.vue'
import { useAppStore } from '@/stores/app'
import { useAuthStore } from '@/stores/auth'
import type { Capability } from '@/utils/permissions'

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
      path: '/city-unavailable',
      name: 'city-unavailable',
      component: () => import('@/views/CityUnavailableView.vue'),
      meta: { skipCity: true },
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
          meta: { capability: 'REPORT_READ' },
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
          meta: { capability: 'GEO_READ' },
        },
        {
          path: 'operations',
          name: 'operations',
          component: () => import('@/views/OperationsTaskView.vue'),
          meta: { capability: 'OPS_READ' },
        },
        {
          path: 'admin',
          name: 'admin',
          component: () => import('@/views/AdminView.vue'),
          meta: { capability: 'ADMIN_READ' },
        },
      ],
    },
  ],
})

router.beforeEach(async (to) => {
  const authStore = useAuthStore()
  const appStore = useAppStore()
  if (!authStore.initialized) await authStore.restore()
  if (to.meta.public) {
    if (!authStore.authenticated) appStore.resetCities()
    return authStore.authenticated ? '/dashboard' : true
  }
  if (!authStore.authenticated) {
    appStore.resetCities()
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (!to.meta.skipCity) {
    try {
      await appStore.ensureCities()
    } catch {
      return { name: 'city-unavailable', query: { redirect: to.fullPath } }
    }
  }
  const capability = to.meta.capability as Capability | undefined
  if (capability && !authStore.can(capability)) return '/dashboard'
  return true
})

export default router

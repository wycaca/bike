import { createRouter, createWebHistory } from 'vue-router'

import AppLayout from '@/layouts/AppLayout.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: AppLayout,
      children: [
        { path: '', redirect: '/map' },
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
      ],
    },
  ],
})

export default router

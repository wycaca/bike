<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

import { useAuthStore } from '@/stores/auth'
import { useAppStore } from '@/stores/app'

const route = useRoute()
const auth = useAuthStore()
const app = useAppStore()
const isAdmin = computed(() => auth.user?.role === 'ADMIN')
const title = computed(() => route.meta.title as string ?? '骑行运营')
const tabs = computed(() => isAdmin.value ? [
  { path: '/admin/overview', icon: 'chart-trending-o', label: '运营' },
  { path: '/vehicles', icon: 'logistics', label: '车辆' },
  { path: '/admin/tasks', icon: 'orders-o', label: '任务' },
  { path: '/admin/control', icon: 'setting-o', label: '管控' },
  { path: '/profile', icon: 'contact-o', label: '我的' },
] : [
  { path: '/operator/pool', icon: 'todo-list-o', label: '任务池' },
  { path: '/operator/work', icon: 'passed', label: '作业' },
  { path: '/operator/route', icon: 'guide-o', label: '路线' },
  { path: '/vehicles', icon: 'logistics', label: '车辆' },
  { path: '/profile', icon: 'contact-o', label: '我的' },
])

function switchCity() {
  app.changeCity(app.cityCode === '110000' ? '310000' : '110000')
}
</script>

<template>
  <div class="mobile-shell">
    <header class="app-header">
      <div><span class="brand-mark">骑行运营</span><h1>{{ title }}</h1></div>
      <button class="city-switch" type="button" data-test="city-switch" @click="switchCity">
        {{ app.cityName }} <van-icon name="exchange" />
      </button>
    </header>
    <main class="app-content"><router-view /></main>
    <van-tabbar :model-value="route.path" route safe-area-inset-bottom>
      <van-tabbar-item v-for="tab in tabs" :key="tab.path" :to="tab.path" :name="tab.path" :icon="tab.icon" :data-test="`nav-${tab.label}`">{{ tab.label }}</van-tabbar-item>
    </van-tabbar>
  </div>
</template>

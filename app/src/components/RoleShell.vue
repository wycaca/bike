<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'

import { useAuthStore } from '@/stores/auth'
import { useAppStore } from '@/stores/app'

const route = useRoute()
const auth = useAuthStore()
const app = useAppStore()
const cityMenuVisible = ref(false)
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

const cityActions = computed(() => app.cities.map((city) => ({ name: city.name, value: city.code })))

/** 输入: 城市选择项; 输出: 切换当前城市并关闭菜单。 */
function selectCity(action: { name: string; value: string }): void {
  app.changeCity(action.value)
  cityMenuVisible.value = false
}
</script>

<template>
  <div class="mobile-shell">
    <header class="app-header">
      <div><span class="brand-mark">骑行运营</span><h1>{{ title }}</h1></div>
      <button class="city-switch" type="button" data-test="city-switch" :disabled="app.cities.length < 2" @click="cityMenuVisible = true">
        {{ app.cityName }} <van-icon name="exchange" />
      </button>
    </header>
    <main class="app-content"><router-view /></main>
    <van-tabbar :model-value="route.path" route safe-area-inset-bottom>
      <van-tabbar-item v-for="tab in tabs" :key="tab.path" :to="tab.path" :name="tab.path" :icon="tab.icon" :data-test="`nav-${tab.label}`">{{ tab.label }}</van-tabbar-item>
    </van-tabbar>
    <van-action-sheet
      v-model:show="cityMenuVisible"
      :actions="cityActions"
      cancel-text="取消"
      close-on-click-action
      @select="selectCity"
    />
  </div>
</template>

<script setup lang="ts">
import {
  Bicycle,
  DataAnalysis,
  DataLine,
  Document,
  Management,
  List,
  Location,
  Operation,
  Tickets,
  SwitchButton,
  UserFilled,
} from '@element-plus/icons-vue'
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useAppStore } from '@/stores/app'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const authStore = useAuthStore()
const environmentLabel = computed(() => appStore.cities.map((city) => city.name).join(' / '))
const activeMenu = computed(() => {
  if (route.path.startsWith('/vehicles')) return '/vehicles'
  if (route.path.startsWith('/reports')) return '/reports/revenue'
  return `/${route.path.split('/')[1] || 'dashboard'}`
})

/** 输入: 账户菜单命令; 输出: 注销时清理会话并返回登录页。 */
async function handleAccountCommand(command: string) {
  if (command !== 'logout') return
  await authStore.signOut()
  await router.replace('/login')
}
</script>

<template>
  <div class="app-shell">
    <aside class="app-sidebar">
      <div class="brand-block" aria-label="骑巡共享电单车管理平台">
        <span class="brand-mark"><Bicycle /></span>
        <span class="brand-name">骑巡</span>
      </div>

      <el-menu class="primary-menu" :default-active="activeMenu" router>
        <el-menu-item index="/dashboard">
          <el-icon><DataAnalysis /></el-icon>
          <span>运营看板</span>
        </el-menu-item>
        <el-menu-item v-if="authStore.can('REPORT_READ')" index="/reports/revenue">
          <el-icon><DataLine /></el-icon>
          <span>收入报表</span>
        </el-menu-item>
        <el-menu-item index="/map">
          <el-icon><Location /></el-icon>
          <span>车辆地图</span>
        </el-menu-item>
        <el-menu-item index="/vehicles">
          <el-icon><List /></el-icon>
          <span>车辆资产</span>
        </el-menu-item>
        <el-menu-item v-if="authStore.can('GEO_READ')" index="/geo">
          <el-icon><Operation /></el-icon>
          <span>围栏与停车点</span>
        </el-menu-item>
        <el-menu-item v-if="authStore.can('OPS_READ')" index="/operations">
          <el-icon><Tickets /></el-icon>
          <span>运维任务</span>
        </el-menu-item>
        <el-menu-item v-if="authStore.can('ADMIN_READ')" index="/admin">
          <el-icon><Document /></el-icon>
          <span>组织与审计</span>
        </el-menu-item>
        <el-menu-item v-if="authStore.can('FLEET_WRITE')" index="/fleet-admin">
          <el-icon><Management /></el-icon>
          <span>城市与车辆扩展</span>
        </el-menu-item>
      </el-menu>

      <div class="sidebar-foot">管理运维端</div>
    </aside>

    <main class="app-main">
      <header class="app-header">
        <div>
          <div class="app-title">共享电单车运营中心</div>
          <div class="environment-label">{{ environmentLabel }}运营</div>
        </div>
        <div class="header-actions">
          <el-icon class="header-status"><Bicycle /></el-icon>
          <el-dropdown trigger="click" @command="handleAccountCommand">
            <button type="button" class="account-block">
              <el-icon><UserFilled /></el-icon>
              <span>{{ authStore.user?.displayName }}</span>
              <small>{{ authStore.user?.orgName }}</small>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>
                  <el-icon><Operation /></el-icon>{{ authStore.user?.role }}
                </el-dropdown-item>
                <el-dropdown-item command="logout" divided>
                  <el-icon><SwitchButton /></el-icon>退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <section class="page-content">
        <RouterView />
      </section>
    </main>
  </div>
</template>

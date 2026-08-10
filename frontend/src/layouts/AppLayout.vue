<script setup lang="ts">
import { Bell, Bicycle, List, Location, UserFilled } from '@element-plus/icons-vue'
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const activeMenu = computed(() => (route.path.startsWith('/vehicles') ? '/vehicles' : '/map'))
</script>

<template>
  <div class="app-shell">
    <aside class="app-sidebar">
      <div class="brand-block" aria-label="骑巡共享电单车管理平台">
        <span class="brand-mark"><Bicycle /></span>
        <span class="brand-name">骑巡</span>
      </div>

      <el-menu class="primary-menu" :default-active="activeMenu" router>
        <el-menu-item index="/map">
          <el-icon><Location /></el-icon>
          <span>车辆地图</span>
        </el-menu-item>
        <el-menu-item index="/vehicles">
          <el-icon><List /></el-icon>
          <span>车辆资产</span>
        </el-menu-item>
      </el-menu>

      <div class="sidebar-foot">管理运维端</div>
    </aside>

    <main class="app-main">
      <header class="app-header">
        <div>
          <div class="app-title">共享电单车运营中心</div>
          <div class="environment-label">北京 / 上海试点</div>
        </div>
        <div class="header-actions">
          <el-tooltip content="告警中心待后端接口完成" placement="bottom">
            <el-button class="header-icon-button" :icon="Bell" circle disabled aria-label="告警" />
          </el-tooltip>
          <div class="account-block">
            <el-icon><UserFilled /></el-icon>
            <span>开发环境</span>
          </div>
        </div>
      </header>

      <section class="page-content">
        <RouterView />
      </section>
    </main>
  </div>
</template>

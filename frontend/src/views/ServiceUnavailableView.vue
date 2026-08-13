<script setup lang="ts">
import { RefreshRight } from '@element-plus/icons-vue'
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useAppStore } from '@/stores/app'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const appStore = useAppStore()
const route = useRoute()
const router = useRouter()
const loading = ref(false)

/** 输入: 当前会话和后端状态; 输出: 恢复后返回原页面，未登录时返回登录页。 */
async function retry(): Promise<void> {
  loading.value = true
  await authStore.restore()
  if (authStore.restoreError) {
    loading.value = false
    return
  }
  if (!authStore.authenticated) {
    await router.replace('/login')
    return
  }
  try {
    await appStore.ensureCities(true)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard'
    await router.replace(redirect)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="error-page">
    <el-result icon="error" title="服务暂时不可用" :sub-title="authStore.restoreError || '连接已恢复，请重新尝试。'">
      <template #extra><el-button type="primary" :icon="RefreshRight" :loading="loading" @click="retry">重新连接</el-button></template>
    </el-result>
  </main>
</template>

<style scoped>.error-page { display:grid; min-height:100vh; place-items:center; background:#f3f6f4; }</style>

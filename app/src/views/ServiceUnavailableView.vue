<script setup lang="ts">
import { showToast } from 'vant'
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useAppStore } from '@/stores/app'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const app = useAppStore()
const route = useRoute()
const router = useRouter()
const loading = ref(false)

/** 输入: 当前会话和后端状态; 输出: 恢复后返回原页面，未登录时返回登录页。 */
async function retry(): Promise<void> {
  loading.value = true
  await auth.bootstrap()
  if (auth.restoreError) {
    showToast(auth.restoreError)
    loading.value = false
    return
  }
  if (!auth.user) {
    await router.replace('/login')
    return
  }
  try {
    await app.ensureCities(true)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    await router.replace(redirect)
  } catch (error) {
    showToast(error instanceof Error ? error.message : '城市加载失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="error-page">
    <van-empty image="network" :description="auth.restoreError || '连接已恢复，请重新尝试'">
      <van-button type="primary" size="small" :loading="loading" @click="retry">重新连接</van-button>
    </van-empty>
  </main>
</template>

<style scoped>.error-page { display:grid; min-height:100vh; place-items:center; background:var(--surface); }</style>

<script setup lang="ts">
import { showToast } from 'vant'
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { errorText } from '@/api'
import { useAppStore } from '@/stores/app'

const app = useAppStore()
const route = useRoute()
const router = useRouter()
const loading = ref(false)

/** 输入: 当前登录会话; 输出: 重新加载城市并返回原目标页面。 */
async function retry(): Promise<void> {
  loading.value = true
  try {
    await app.ensureCities(true)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    await router.replace(redirect)
  } catch (error) {
    showToast(errorText(error))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="city-unavailable">
    <van-empty image="error" description="当前账号没有可用城市，或城市配置暂时无法加载">
      <van-button type="primary" size="small" :loading="loading" @click="retry">重新加载</van-button>
    </van-empty>
  </main>
</template>

<style scoped>
.city-unavailable { display: grid; min-height: 100vh; place-items: center; background: var(--surface); }
</style>

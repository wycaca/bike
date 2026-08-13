<script setup lang="ts">
import { RefreshRight } from '@element-plus/icons-vue'
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { errorMessage } from '@/api/http'
import { useAppStore } from '@/stores/app'

const appStore = useAppStore()
const route = useRoute()
const router = useRouter()
const loading = ref(false)
const error = ref('当前账号没有可用城市，或城市配置暂时无法加载。')

/** 输入: 当前登录会话; 输出: 重新加载城市并返回原目标页面。 */
async function retry(): Promise<void> {
  loading.value = true
  try {
    await appStore.ensureCities(true)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard'
    await router.replace(redirect)
  } catch (cause) {
    error.value = errorMessage(cause)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="city-unavailable">
    <el-result icon="warning" title="运营城市不可用" :sub-title="error">
      <template #extra>
        <el-button type="primary" :icon="RefreshRight" :loading="loading" @click="retry">
          重新加载
        </el-button>
      </template>
    </el-result>
  </main>
</template>

<style scoped>
.city-unavailable {
  display: grid;
  min-height: 100vh;
  place-items: center;
  background: #f3f6f4;
}
</style>

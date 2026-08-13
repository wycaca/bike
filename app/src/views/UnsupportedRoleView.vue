<script setup lang="ts">
import { useRouter } from 'vue-router'

import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()

/** 输入: 当前移动端会话; 输出: 注销并返回登录页。 */
async function logout() {
  await auth.signOut()
  await router.replace('/login')
}
</script>

<template>
  <main class="unsupported-role">
    <van-icon name="desktop-o" size="48" />
    <h1>请使用桌面管理端</h1>
    <p>审计人员仅支持桌面端的报表与审计功能，移动作业端不提供相关入口。</p>
    <van-button block type="primary" @click="logout">返回登录</van-button>
  </main>
</template>

<style scoped>
.unsupported-role { display: grid; min-height: 100vh; place-content: center; gap: 16px; padding: 32px; text-align: center; }
.unsupported-role p { max-width: 320px; color: #66736f; line-height: 1.7; }
</style>

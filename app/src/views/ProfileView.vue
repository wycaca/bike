<script setup lang="ts">
import { showConfirmDialog, showToast } from 'vant'
import { useRouter } from 'vue-router'

import { errorText } from '@/api'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const roleLabels = { ADMIN: '管理人员', OPERATOR: '运维人员', AUDITOR: '审计人员' } as const

/** 输入: 当前登录会话; 输出: 注销会话并返回登录页。 */
async function logout() {
  try {
    await showConfirmDialog({ title: '退出登录', message: '确认结束当前登录会话？' })
    await auth.signOut()
    await router.replace('/login')
  } catch (error) {
    if (error !== 'cancel') showToast(errorText(error))
  }
}
</script>

<template>
  <div>
    <section class="profile-head">
      <van-icon name="user-o" size="30" />
      <h2>{{ auth.user?.displayName }}</h2>
      <p>{{ auth.user ? roleLabels[auth.user.role] : '' }} · {{ auth.user?.orgName }}</p>
    </section>
    <div class="form-block content-gap">
      <van-cell title="账号" :value="auth.user?.username" />
      <van-cell title="所属组织" :value="auth.user?.orgName" />
      <van-cell title="当前角色" :value="auth.user ? roleLabels[auth.user.role] : ''" />
    </div>
    <van-button block plain type="danger" data-test="logout-button" @click="logout"><van-icon name="close" /> 退出登录</van-button>
  </div>
</template>

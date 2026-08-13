<script setup lang="ts">
import { Bicycle, Lock, User } from '@element-plus/icons-vue'
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { errorMessage } from '@/api/http'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const loading = ref(false)
const error = ref(route.query.reason === 'expired' ? '登录状态已过期，请重新登录' : '')
const form = reactive({ username: 'admin', password: '' })

/** 输入: 登录表单; 输出: 成功进入目标页面，失败显示错误。 */
async function submit() {
  if (!form.username.trim() || !form.password) {
    error.value = '请输入用户名和密码'
    return
  }
  loading.value = true
  error.value = ''
  try {
    await authStore.signIn(form.username.trim(), form.password)
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
  <main class="login-page">
    <section class="login-brand">
      <div class="login-symbol"><Bicycle /></div>
      <h1>骑巡</h1>
      <p>共享电单车运营管理平台</p>
      <dl>
        <div><dt>资产</dt><dd>车辆全生命周期</dd></div>
        <div><dt>多城</dt><dd>动态运营范围</dd></div>
        <div><dt>实时</dt><dd>位置与状态</dd></div>
      </dl>
    </section>
    <section class="login-panel">
      <form class="login-form" @submit.prevent="submit">
        <div>
          <span class="login-kicker">运营管理端</span>
          <h2>登录工作台</h2>
          <p>使用平台管理员分配的账号登录</p>
        </div>
        <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
        <el-input v-model="form.username" size="large" :prefix-icon="User" placeholder="用户名" />
        <el-input
          v-model="form.password"
          size="large"
          :prefix-icon="Lock"
          type="password"
          show-password
          placeholder="密码"
          @keyup.enter="submit"
        />
        <el-button type="primary" size="large" native-type="submit" :loading="loading">
          登录
        </el-button>
      </form>
    </section>
  </main>
</template>

<style scoped>
.login-page {
  display: grid;
  grid-template-columns: minmax(360px, 44%) minmax(420px, 1fr);
  min-height: 100%;
  background: #f4f6f5;
}

.login-brand {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: clamp(48px, 8vw, 110px);
  color: #f4fbf8;
  background: #143329;
}

.login-symbol {
  display: grid;
  place-items: center;
  width: 58px;
  height: 58px;
  color: #15372c;
  background: #6bd2a5;
  border-radius: 8px;
}

.login-symbol svg { width: 36px; height: 36px; }
.login-brand h1 { margin: 24px 0 4px; font-size: 46px; letter-spacing: 0; }
.login-brand > p { margin: 0; color: #b9d0c7; font-size: 17px; }
.login-brand dl { display: flex; gap: 34px; margin: 54px 0 0; }
.login-brand dl div { padding-left: 14px; border-left: 2px solid #4ca983; }
.login-brand dt { font-size: 22px; font-weight: 700; }
.login-brand dd { margin: 4px 0 0; color: #a9c1b8; font-size: 12px; }

.login-panel { display: grid; place-items: center; padding: 40px; }
.login-form { display: grid; gap: 18px; width: min(380px, 100%); }
.login-kicker { color: var(--brand); font-size: 12px; font-weight: 700; }
.login-form h2 { margin: 8px 0 5px; font-size: 26px; }
.login-form p { margin: 0; color: var(--muted); font-size: 13px; }
.login-form .el-button { width: 100%; margin-top: 4px; }

@media (max-width: 760px) {
  .login-page { grid-template-columns: 1fr; }
  .login-brand { min-height: 260px; padding: 34px; }
  .login-brand dl { margin-top: 30px; }
  .login-panel { align-items: start; padding: 38px 24px; }
}
</style>

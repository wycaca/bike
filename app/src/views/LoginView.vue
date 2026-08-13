<script setup lang="ts">
import { reactive, ref } from 'vue'
import { showFailToast } from 'vant'
import { useRoute, useRouter } from 'vue-router'

import { errorText } from '@/api'
import { roleHome } from '@/router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

if (route.query.reason === 'expired') showFailToast('登录状态已过期，请重新登录')

/** 输入: 登录表单; 输出: 按后端角色进入管理员或运维人员首页。 */
async function submit() {
  if (!form.username.trim() || !form.password) {
    showFailToast('请输入账号和密码')
    return
  }
  loading.value = true
  try {
    const user = await auth.signIn(form.username.trim(), form.password)
    await router.replace(roleHome(user.role))
  } catch (error) {
    showFailToast(errorText(error))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-view">
    <section class="login-brand">
      <div class="brand-symbol"><van-icon name="logistics" /></div>
      <h1>骑行运营</h1>
      <p>共享电单车移动作业端</p>
    </section>
    <van-form class="login-form" @submit="submit">
      <van-cell-group inset>
        <van-field v-model="form.username" name="username" label="账号" placeholder="请输入账号" autocomplete="username" data-test="username" />
        <van-field v-model="form.password" name="password" label="密码" type="password" placeholder="请输入密码" autocomplete="current-password" data-test="password" />
      </van-cell-group>
      <div class="login-action"><van-button block type="primary" native-type="submit" :loading="loading" data-test="login-button">登录</van-button></div>
    </van-form>
  </div>
</template>

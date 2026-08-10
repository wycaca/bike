import { defineStore } from 'pinia'
import { ref } from 'vue'

import * as api from '@/api'
import type { CurrentUser } from '@/types'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<CurrentUser | null>(null)
  const initialized = ref(false)

  /** 输入: 无; 输出: 恢复后端会话，未登录时保持空用户。 */
  async function bootstrap() {
    try { user.value = await api.currentUser() } catch { user.value = null }
    initialized.value = true
  }

  /** 输入: 用户名和密码; 输出: 登录用户及其角色。 */
  async function signIn(username: string, password: string) {
    user.value = await api.login(username, password)
    initialized.value = true
    return user.value
  }

  async function signOut() {
    await api.logout()
    user.value = null
  }

  return { user, initialized, bootstrap, signIn, signOut }
})

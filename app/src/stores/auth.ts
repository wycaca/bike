import { defineStore } from 'pinia'
import { ref } from 'vue'
import axios from 'axios'

import * as api from '@/api'
import type { CurrentUser } from '@/types'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<CurrentUser | null>(null)
  const initialized = ref(false)
  const restoreError = ref('')

  /** 输入: 无; 输出: 恢复后端会话，未登录时保持空用户。 */
  async function bootstrap() {
    restoreError.value = ''
    try { user.value = await api.currentUser() } catch (error) {
      const status = axios.isAxiosError(error) ? error.response?.status : undefined
      if (status === 401 || status === 403) user.value = null
      else restoreError.value = api.errorText(error)
    }
    initialized.value = true
  }

  /** 输入: 用户名和密码; 输出: 登录用户及其角色。 */
  async function signIn(username: string, password: string) {
    user.value = await api.login(username, password)
    initialized.value = true
    restoreError.value = ''
    return user.value
  }

  async function signOut() {
    await api.logout()
    user.value = null
  }

  /** 输入: 无; 输出: 立即清理本地会话状态，不发起网络请求。 */
  function clearSession() {
    user.value = null
    initialized.value = true
    restoreError.value = ''
  }

  return { user, initialized, restoreError, bootstrap, signIn, signOut, clearSession }
})

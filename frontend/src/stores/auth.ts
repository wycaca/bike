import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

import * as authApi from '@/api/auth'
import type { CurrentUser, UserRole } from '@/types/operations'
import { hasCapability, type Capability } from '@/utils/permissions'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<CurrentUser | null>(null)
  const initialized = ref(false)
  const authenticated = computed(() => user.value !== null)

  /** 输入: 无; 输出: 当前用户, 未登录时为 null。 */
  async function restore(): Promise<CurrentUser | null> {
    try {
      user.value = await authApi.getCurrentUser()
    } catch {
      user.value = null
    } finally {
      initialized.value = true
    }
    return user.value
  }

  /** 输入: 登录凭据; 输出: 登录用户。 */
  async function signIn(username: string, password: string): Promise<CurrentUser> {
    user.value = await authApi.login(username, password)
    initialized.value = true
    return user.value
  }

  /** 输入: 无; 输出: 无, 清理服务端和本地会话。 */
  async function signOut(): Promise<void> {
    try {
      await authApi.logout()
    } finally {
      user.value = null
      initialized.value = true
    }
  }

  /** 输入: 允许角色; 输出: 当前用户是否拥有其中任意角色。 */
  function hasRole(...roles: UserRole[]): boolean {
    return user.value !== null && roles.includes(user.value.role)
  }

  /** 输入: 平台能力; 输出: 当前用户是否拥有该能力。 */
  function can(capability: Capability): boolean {
    return hasCapability(user.value?.role, capability, user.value?.dataScope)
  }

  return { user, initialized, authenticated, restore, signIn, signOut, hasRole, can }
})

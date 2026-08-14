import axios from 'axios'
import { createPinia, setActivePinia } from 'pinia'

import * as api from '@/api'
import { useAuthStore } from '@/stores/auth'
import type { CurrentUser } from '@/types'

vi.mock('@/api', () => ({
  currentUser: vi.fn(),
  errorText: () => '服务暂时不可用',
  login: vi.fn(),
  logout: vi.fn(),
}))

const existingUser = { userId: 'USR-1', username: 'operator' } as CurrentUser

describe('移动端会话恢复', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('临时服务故障保留已有用户并提供错误信息', async () => {
    vi.mocked(api.currentUser).mockRejectedValue(new axios.AxiosError('network', 'ERR_NETWORK'))
    const store = useAuthStore()
    store.user = existingUser

    await store.bootstrap()

    expect(store.user).toEqual(existingUser)
    expect(store.restoreError).toBe('服务暂时不可用')
  })

  it('认证失效时清理已有用户', async () => {
    const error = new axios.AxiosError('unauthorized')
    error.response = { status: 401 } as never
    vi.mocked(api.currentUser).mockRejectedValue(error)
    const store = useAuthStore()
    store.user = existingUser

    await store.bootstrap()

    expect(store.user).toBeNull()
    expect(store.restoreError).toBe('')
  })
})

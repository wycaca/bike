import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { getCities } from '@/api/city'
import { useAppStore } from '@/stores/app'

vi.mock('@/api/city', () => ({ getCities: vi.fn() }))

describe('动态城市状态', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('使用接口返回的首个城市及组织和地图配置', async () => {
    vi.mocked(getCities).mockResolvedValue([{
      code: '440100', name: '广州', orgId: 'ORG-GZ', orgName: '广州运营中心',
      center: [113.2644, 23.1291], bounds: [113.1, 23, 113.5, 23.3],
    }])
    const store = useAppStore()

    await store.ensureCities()

    expect(store.cityCode).toBe('440100')
    expect(store.currentCity.orgId).toBe('ORG-GZ')
    expect(store.currentCity.bounds).toEqual([113.1, 23, 113.5, 23.3])
  })
})

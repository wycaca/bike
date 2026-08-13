import { createPinia, setActivePinia } from 'pinia'

import { getCities } from '@/api'
import { useAppStore } from '@/stores/app'

vi.mock('@/api', () => ({ getCities: vi.fn() }))

describe('移动端动态城市状态', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('使用接口返回的首个城市及地图参数', async () => {
    vi.mocked(getCities).mockResolvedValue([{
      code: '440100', name: '广州', orgId: 'ORG-GZ', orgName: '广州运营中心',
      center: [113.2644, 23.1291], bounds: [113.1, 23, 113.5, 23.3],
    }])
    const store = useAppStore()

    await store.ensureCities()

    expect(store.cityCode).toBe('440100')
    expect(store.cityName).toBe('广州')
    expect(store.currentCity.center).toEqual([113.2644, 23.1291])
  })
})

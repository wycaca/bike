import { load } from '@amap/amap-jsapi-loader'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@amap/amap-jsapi-loader', () => ({ load: vi.fn() }))

describe('高德地图加载恢复', () => {
  beforeEach(() => {
    vi.resetModules()
    vi.clearAllMocks()
    vi.stubEnv('VITE_AMAP_KEY', 'test-key')
    vi.stubGlobal('window', {
      location: { origin: 'http://localhost' },
      setTimeout,
      clearTimeout,
    })
  })

  it('首次重试仍失败后允许下一次调用重新加载', async () => {
    vi.mocked(load)
      .mockRejectedValueOnce(new Error('network error'))
      .mockRejectedValueOnce(new Error('network error'))
      .mockResolvedValueOnce({} as never)
    const { loadAmap } = await import('./amap')

    await expect(loadAmap()).rejects.toThrow('高德地图加载失败')
    await expect(loadAmap()).resolves.toBeDefined()

    expect(load).toHaveBeenCalledTimes(3)
  })
})

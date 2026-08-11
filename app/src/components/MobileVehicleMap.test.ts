import { flushPromises, mount } from '@vue/test-utils'

import MobileVehicleMap from '@/components/MobileVehicleMap.vue'
import type { MapMarker } from '@/types'

vi.mock('@/amap', () => ({ amapConfigured: () => true, loadAmap: vi.fn().mockResolvedValue(undefined) }))

let activeMap: FakeMap

class FakeMarker {
  constructor(public options: { content: HTMLElement }) {}
}

class FakeMap {
  zoom = 12
  setZoomAndCenter = vi.fn()
  constructor(private container: HTMLElement) { activeMap = this }
  add(overlays: FakeMarker[]) { overlays.forEach((overlay) => this.container.appendChild(overlay.options.content)) }
  remove(overlays: FakeMarker[]) { overlays.forEach((overlay) => overlay.options.content.remove()) }
  on() {}
  destroy() {}
  getZoom() { return this.zoom }
  getBounds() {
    return {
      getSouthWest: () => ({ getLng: () => 116.2, getLat: () => 39.8 }),
      getNorthEast: () => ({ getLng: () => 116.6, getLat: () => 40.1 }),
    }
  }
}

const vehicleMarker: MapMarker = {
  markerType: 'VEHICLE', markerId: 'BIKE-001', vehicleId: 'BIKE-001', longitude: 116.4, latitude: 39.9,
  vehicleCount: 1, lowBatteryCount: 0, faultCount: 0, batteryPercent: 80, lifecycleStatus: 'OPERATING',
  latestState: null,
}

describe('移动车辆地图标记交互', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    ;(window as unknown as { AMap: unknown }).AMap = {
      Map: FakeMap,
      Marker: FakeMarker,
      Pixel: class {},
    }
  })

  it('点击单车 DOM 标记后返回车辆详情', async () => {
    const wrapper = mount(MobileVehicleMap, { props: { markers: [vehicleMarker], center: [116.4, 39.9], zoom: 12, loading: false } })
    await flushPromises()
    await wrapper.get('.mobile-map-marker').trigger('click')

    expect(wrapper.emitted('select')?.[0]).toEqual([vehicleMarker])
  })

  it('点击聚合标记后放大并居中', async () => {
    const cluster = { ...vehicleMarker, markerType: 'CLUSTER' as const, vehicleId: null, vehicleCount: 8 }
    const wrapper = mount(MobileVehicleMap, { props: { markers: [cluster], center: [116.4, 39.9], zoom: 12, loading: false } })
    await flushPromises()
    await wrapper.get('.mobile-map-marker').trigger('click')

    expect(activeMap.setZoomAndCenter).toHaveBeenCalledWith(14, [116.4, 39.9])
    expect(wrapper.emitted('select')).toBeUndefined()
  })
})

import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

import * as api from '@/api'
import type { MapMarker, Vehicle } from '@/types'
import VehicleView from '@/views/VehicleView.vue'

vi.mock('@/api', () => ({
  getMapVehicles: vi.fn(),
  getVehicles: vi.fn(),
  errorText: (error: unknown) => String(error),
}))

vi.mock('@/components/MobileVehicleMap.vue', () => ({
  default: {
    props: ['markers', 'center', 'zoom', 'loading'],
    emits: ['select', 'viewportChange'],
    template: '<button data-test="mock-vehicle-map" @click="$emit(\'select\', markers[0])">地图标记 {{ markers.length }}</button>',
  },
}))

const marker: MapMarker = {
  markerType: 'VEHICLE', markerId: 'BIKE-001', vehicleId: 'BIKE-001',
  longitude: 116.4074, latitude: 39.9042, vehicleCount: 1, lowBatteryCount: 1,
  faultCount: 0, batteryPercent: 18, lifecycleStatus: 'OPERATING',
  latestState: {
    reportedAt: '2026-08-11T10:00:00Z', longitude: 116.4074, latitude: 39.9042,
    batteryPercent: 18, rideStatus: 'IDLE', controllerStatus: 'NORMAL', online: true, faultCodes: [],
  },
}

const vehicle: Vehicle = {
  vehicleId: 'BIKE-001', plateNumber: '京A00001', lifecycleStatus: 'OPERATING',
  latestState: { batteryPercent: 18, online: true, controllerStatus: 'NORMAL' },
}

describe('车辆地图与列表切换', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(api.getMapVehicles).mockResolvedValue({ markers: [marker], clustered: false, coordinateSystem: 'GCJ02' })
    vi.mocked(api.getVehicles).mockResolvedValue([vehicle])
  })

  it('默认加载北京视野内的高德地图车辆', async () => {
    const wrapper = mount(VehicleView)
    await flushPromises()

    expect(api.getMapVehicles).toHaveBeenCalledWith(expect.objectContaining({
      minLongitude: 116.2, minLatitude: 39.8, maxLongitude: 116.6, maxLatitude: 40.1,
      zoom: 12, coordinateSystem: 'GCJ02',
    }), expect.any(AbortSignal))
    expect(wrapper.get('[data-test="mock-vehicle-map"]').text()).toContain('地图标记 1')
    expect(wrapper.text()).toContain('1辆车')
  })

  it('切换到列表后加载车辆查询结果', async () => {
    const wrapper = mount(VehicleView)
    await flushPromises()
    await wrapper.get('[data-test="vehicle-list-mode"]').trigger('click')
    await flushPromises()

    expect(api.getVehicles).toHaveBeenCalledWith('110000', '')
    expect(wrapper.text()).toContain('BIKE-001')
    expect(wrapper.text()).toContain('京A00001')
  })
})

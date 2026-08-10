import { describe, expect, it } from 'vitest'

import type { TrajectoryPoint } from '@/types/vehicle'
import { trajectoryDistanceKm } from '@/utils/vehicle'

function point(longitude: number, latitude: number): TrajectoryPoint {
  return {
    reportedAt: '2026-01-01T00:00:00Z',
    longitude,
    latitude,
    accuracyMeters: null,
    speedKmh: null,
    directionDegrees: null,
    batteryPercent: null,
    lockStatus: 'LOCKED',
    rideStatus: 'IDLE',
    coordinateSystem: 'WGS84',
  }
}

describe('trajectoryDistanceKm', () => {
  it('按相邻轨迹点计算球面距离', () => {
    expect(trajectoryDistanceKm([])).toBe(0)
    expect(trajectoryDistanceKm([point(116.4, 39.9), point(116.4, 39.9)])).toBe(0)
    expect(trajectoryDistanceKm([point(116.4, 39.9), point(116.4, 40.0)])).toBeCloseTo(11.12, 1)
  })
})

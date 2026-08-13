import { describe, expect, it } from 'vitest'

import { parseVehicleCsv, VEHICLE_CSV_HEADERS } from '@/utils/fleet'

describe('车辆 CSV 预检', () => {
  it('支持扩展城市并阻止文件内重复锁编号', () => {
    const first = 'BIKE-GZ-1,COMPANY,LOCK-GZ-1,CTRL-GZ-1,,,YD-DEMO,BATCH-GZ,440100,440106,2026-08-01,PENDING'
    const second = 'BIKE-GZ-2,COMPANY,LOCK-GZ-1,CTRL-GZ-2,,,YD-DEMO,BATCH-GZ,440100,440106,2026-08-01,PENDING'

    const result = parseVehicleCsv(
      [VEHICLE_CSV_HEADERS.join(','), first, second].join('\n'),
      new Set(['440100']),
    )

    expect(result.rows).toHaveLength(1)
    expect(result.rows[0]?.operationCityCode).toBe('440100')
    expect(result.errors[0]).toContain('编号在文件内重复')
  })
})

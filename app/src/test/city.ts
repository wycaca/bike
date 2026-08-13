import { useAppStore } from '@/stores/app'
import type { CityDefinition } from '@/types'

export const testCity: CityDefinition = {
  code: '110000',
  name: '北京',
  orgId: 'ORG-BJ',
  orgName: '北京运营中心',
  center: [116.4074, 39.9042],
  bounds: [116.2, 39.8, 116.6, 40.1],
}

/** 输入: 已激活的测试 Pinia; 输出: 注入一条后端城市响应等价数据。 */
export function seedTestCity(): void {
  const app = useAppStore()
  app.cities = [testCity]
  app.cityCode = testCity.code
}

import { createPinia, defineStore } from 'pinia'
import { computed, ref } from 'vue'

import { getCities } from '@/api/city'
import type { CityDefinition } from '@/types/vehicle'

export const pinia = createPinia()

export const useAppStore = defineStore('app', () => {
  const cities = ref<CityDefinition[]>([])
  const cityCode = ref('')
  let loadingPromise: Promise<void> | null = null

  const currentCity = computed<CityDefinition>(() => {
    const city = cities.value.find((item) => item.code === cityCode.value) ?? cities.value[0]
    if (!city) throw new Error('当前账号没有可用的运营城市')
    return city
  })
  const cityName = computed(() => currentCity.value.name)

  /** 输入: 是否强制刷新; 输出: 后端授权的城市列表，并选择首个有效城市。 */
  async function ensureCities(force = false): Promise<void> {
    if (!force && cities.value.length > 0) return
    if (!force && loadingPromise) return loadingPromise
    loadingPromise = getCities().then((items) => {
      cities.value = items
      if (!items.some((item) => item.code === cityCode.value)) cityCode.value = items[0]?.code ?? ''
      if (items.length === 0) throw new Error('当前账号没有可用的运营城市')
    }).finally(() => { loadingPromise = null })
    return loadingPromise
  }

  /** 输入: 城市代码; 输出: 切换至有权限访问的城市。 */
  function changeCity(code: string): void {
    if (cities.value.some((item) => item.code === code)) cityCode.value = code
  }

  /** 输入: 城市代码; 输出: 对应城市名，未知代码原样返回。 */
  function cityNameFor(code: string): string {
    return cities.value.find((item) => item.code === code)?.name ?? code
  }

  /** 输入: 无; 输出: 清除上一登录会话缓存的城市权限。 */
  function resetCities(): void {
    cities.value = []
    cityCode.value = ''
    loadingPromise = null
  }

  return { cities, cityCode, currentCity, cityName, ensureCities, changeCity, cityNameFor, resetCities }
})

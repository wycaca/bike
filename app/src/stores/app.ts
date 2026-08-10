import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAppStore = defineStore('app', () => {
  const cityCode = ref('110000')
  const cityName = ref('北京')

  function changeCity(code: string) {
    cityCode.value = code
    cityName.value = code === '310000' ? '上海' : '北京'
  }

  return { cityCode, cityName, changeCity }
})

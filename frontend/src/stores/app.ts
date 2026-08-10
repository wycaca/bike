import { createPinia, defineStore } from 'pinia'
import { ref } from 'vue'

export const pinia = createPinia()

export const useAppStore = defineStore('app', () => {
  const cityCode = ref<'110000' | '310000'>('110000')

  return { cityCode }
})

import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'

import App from './App.vue'
import router from './router'
import { pinia, useAppStore } from './stores/app'
import { useAuthStore } from './stores/auth'

import 'element-plus/dist/index.css'
import './styles.css'

const app = createApp(App)
app.use(pinia).use(router).use(ElementPlus, { locale: zhCn })

window.addEventListener('auth-expired', () => {
  const authStore = useAuthStore(pinia)
  if (!authStore.authenticated || router.currentRoute.value.path === '/login') return
  authStore.clearSession()
  useAppStore(pinia).resetCities()
  void router.replace({ path: '/login', query: { reason: 'expired', redirect: router.currentRoute.value.fullPath } })
})

app.mount('#app')

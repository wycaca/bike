import { createPinia } from 'pinia'
import { createApp } from 'vue'
import 'vant/lib/index.css'

import App from './App.vue'
import { router } from './router'
import { useAppStore } from './stores/app'
import { useAuthStore } from './stores/auth'
import './styles.css'

const pinia = createPinia()
const app = createApp(App)
app.use(pinia).use(router)

window.addEventListener('auth-expired', () => {
  const auth = useAuthStore(pinia)
  if (!auth.user || router.currentRoute.value.path === '/login') return
  auth.clearSession()
  useAppStore(pinia).resetCities()
  void router.replace({ path: '/login', query: { reason: 'expired', redirect: router.currentRoute.value.fullPath } })
})

app.mount('#app')

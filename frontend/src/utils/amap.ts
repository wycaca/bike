import { load } from '@amap/amap-jsapi-loader'

const amapKey = import.meta.env.VITE_AMAP_KEY?.trim() ?? ''
let amapPromise: ReturnType<typeof load> | null = null

export function amapConfigured(): boolean {
  return Boolean(amapKey)
}

export function loadAmap() {
  if (!amapKey) return Promise.reject(new Error('未配置高德地图 Key'))

  const serviceHost = import.meta.env.VITE_AMAP_SERVICE_HOST?.trim()
  if (serviceHost) {
    const securityWindow = window as Window & {
      _AMapSecurityConfig?: { serviceHost: string }
    }
    securityWindow._AMapSecurityConfig = { serviceHost }
  }

  amapPromise ??= load({ key: amapKey, version: '2.0', plugins: [] })
  return amapPromise
}

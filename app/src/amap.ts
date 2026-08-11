import { load } from '@amap/amap-jsapi-loader'

const amapKey = import.meta.env.VITE_AMAP_KEY?.trim() ?? ''
let amapPromise: ReturnType<typeof load> | null = null

/** 输入: 无; 输出: 当前移动端构建是否包含高德地图 Key。 */
export function amapConfigured(): boolean {
  return Boolean(amapKey)
}

/** 输入: 移动端高德环境变量; 输出: 已加载的高德 JS API 2.0。 */
export function loadAmap() {
  if (!amapKey) return Promise.reject(new Error('未配置高德地图 Key'))

  const configuredServiceHost = import.meta.env.VITE_AMAP_SERVICE_HOST?.trim()
  const serviceHost = configuredServiceHost
    ? new URL(configuredServiceHost, window.location.origin).toString().replace(/\/$/, '')
    : ''
  if (serviceHost) {
    const securityWindow = window as Window & { _AMapSecurityConfig?: { serviceHost: string } }
    securityWindow._AMapSecurityConfig = { serviceHost }
  }

  amapPromise ??= load({ key: amapKey, version: '2.0', plugins: [] })
  return amapPromise
}

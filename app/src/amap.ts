import { load } from '@amap/amap-jsapi-loader'

const amapKey = import.meta.env.VITE_AMAP_KEY?.trim() ?? ''
let amapPromise: ReturnType<typeof load> | null = null
const LOAD_TIMEOUT_MS = 8_000
const MAX_ATTEMPTS = 2

/** 输入: 无; 输出: 当前移动端构建是否包含高德地图 Key。 */
export function amapConfigured(): boolean {
  return Boolean(amapKey)
}

/** 输入: 高德加载 Promise; 输出: 受 8 秒上限保护的加载结果。 */
async function withTimeout(promise: ReturnType<typeof load>): ReturnType<typeof load> {
  let timer: number | undefined
  try {
    return await Promise.race([
      promise,
      new Promise<never>((_, reject) => {
        timer = window.setTimeout(() => reject(new Error('高德地图加载超时')), LOAD_TIMEOUT_MS)
      }),
    ])
  } finally {
    window.clearTimeout(timer)
  }
}

/** 输入: 移动端高德环境变量; 输出: 自动重试后的高德 JS API 2.0。 */
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

  amapPromise ??= (async () => {
    for (let attempt = 1; attempt <= MAX_ATTEMPTS; attempt += 1) {
      try {
        return await withTimeout(load({ key: amapKey, version: '2.0', plugins: [] }))
      } catch {
        if (attempt === MAX_ATTEMPTS) throw new Error('高德地图加载失败，已自动重试，请检查网络后重试')
        await new Promise((resolve) => window.setTimeout(resolve, 300))
      }
    }
    throw new Error('高德地图加载失败')
  })().catch((error) => {
    amapPromise = null
    throw error
  })
  return amapPromise
}

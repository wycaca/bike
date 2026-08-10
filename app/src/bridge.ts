type NativeResult = { ok: boolean; code?: string; message?: string; data?: unknown }

const pending = new Map<string, { resolve: (value: unknown) => void; reject: (reason: Error) => void }>()

window.BikeNative = {
  onResult(callbackId: string, payload: string) {
    const callback = pending.get(callbackId)
    if (!callback) return
    pending.delete(callbackId)
    const result = JSON.parse(payload) as NativeResult
    if (result.ok) callback.resolve(result.data)
    else callback.reject(new Error(result.message || result.code || '原生能力调用失败'))
  },
}

/** 输入: 原生 Bridge 方法; 输出: 带超时和明确错误的 Promise 结果。 */
function invoke(method: 'requestLocation' | 'scanVehicleCode') {
  if (!window.BikeBridge) return Promise.reject(new Error('当前环境未提供原生能力'))
  const callbackId = crypto.randomUUID()
  return new Promise<unknown>((resolve, reject) => {
    pending.set(callbackId, { resolve, reject })
    window.setTimeout(() => {
      if (pending.delete(callbackId)) reject(new Error('原生能力调用超时'))
    }, 15_000)
    window.BikeBridge![method](callbackId)
  })
}

/** 输入: 无; 输出: 原生定位或浏览器定位的经纬度。 */
export async function requestLocation() {
  if (window.BikeBridge) return invoke('requestLocation') as Promise<{ longitude: number; latitude: number }>
  if (!navigator.geolocation) throw new Error('当前设备未提供定位能力')
  return new Promise<{ longitude: number; latitude: number }>((resolve, reject) => {
    navigator.geolocation.getCurrentPosition(
      (position) => resolve({ longitude: position.coords.longitude, latitude: position.coords.latitude }),
      () => reject(new Error('定位失败，请检查系统定位权限')),
      { enableHighAccuracy: true, timeout: 12_000 },
    )
  })
}

export async function scanVehicleCode() {
  const result = await invoke('scanVehicleCode') as { text: string }
  return result.text
}

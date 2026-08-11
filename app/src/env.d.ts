/// <reference types="vite/client" />
/// <reference types="@amap/amap-jsapi-types" />

interface ImportMetaEnv {
  readonly VITE_AMAP_KEY?: string
  readonly VITE_AMAP_SERVICE_HOST?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

interface Window {
  BikeBridge?: {
    postMessage(message: string): void
  }
  BikeNative?: {
    onResult(callbackId: string, payload: string): void
  }
}

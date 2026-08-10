/// <reference types="vite/client" />

interface Window {
  BikeBridge?: {
    requestLocation(callbackId: string): void
    scanVehicleCode(callbackId: string): void
  }
  BikeNative?: {
    onResult(callbackId: string, payload: string): void
  }
}

import { fileURLToPath, URL } from 'node:url'

import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { VantResolver } from 'unplugin-vue-components/resolvers'
import { defineConfig } from 'vitest/config'

const vantResolver = VantResolver({ importStyle: false })

export default defineConfig({
  plugins: [
    vue(),
    Components({ resolvers: [vantResolver], dts: false }),
  ],
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  server: {
    port: 5174,
    proxy: { '/api': { target: 'http://localhost:8080', changeOrigin: true } },
  },
  test: {
    environment: 'happy-dom',
    globals: true,
    css: true,
    testTimeout: 10_000,
  },
})

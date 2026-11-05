import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// 开发代理：/ingest、/t、/a、/auth 全部经网关（8080）分发（meterflow.md #7.1）
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) }
  },
  server: {
    port: 5173,
    proxy: {
      '/ingest': { target: 'http://localhost:8080', changeOrigin: true },
      '/auth': { target: 'http://localhost:8080', changeOrigin: true },
      '/t': { target: 'http://localhost:8080', changeOrigin: true },
      '/a': { target: 'http://localhost:8080', changeOrigin: true }
    }
  }
})

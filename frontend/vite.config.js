import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    host: '0.0.0.0',
    port: 5173,
    strictPort: true,
    // 允许局域网 IP 直接访问；如使用临时隧道，允许常见隧道域名。
    allowedHosts: ['.trycloudflare.com', '.ngrok-free.app', '.ngrok.io'],
    watch: {
      ignored: ['**/.git/**', '**/node_modules/**', '**/.*.tmpdir/**']
    },
    proxy: {
      '/api': {
        target: process.env.VITE_API_TARGET || 'http://localhost:8080',
        changeOrigin: true
      },
      '/ws': {
        target: 'ws://localhost:8080',
        ws: true,
        changeOrigin: true
      }
    }
  }
})
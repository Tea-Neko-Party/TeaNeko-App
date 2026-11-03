import {defineConfig} from 'vite'
import vue from '@vitejs/plugin-vue'
import basicSsl from '@vitejs/plugin-basic-ssl'

// 2026/11/03：开发服务器仅绑定本机，用 HTTPS 同源代理访问后端，生产只发布静态产物。
export default defineConfig({
  base: '/console/',
  plugins: [vue(), basicSsl()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api/': {
        target: 'http://127.0.0.1:6691',
        changeOrigin: false,
        headers: { 'X-Forwarded-Proto': 'https', 'X-Forwarded-Host': 'localhost:5173' },
      },
    },
  },
  build: {
    outDir: '../build/generated/web/console',
    emptyOutDir: true,
    sourcemap: false,
  },
})

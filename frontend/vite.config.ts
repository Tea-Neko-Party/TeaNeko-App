import {defineConfig} from 'vite'
import vue from '@vitejs/plugin-vue'
import basicSsl from '@vitejs/plugin-basic-ssl'
import {readFileSync} from 'node:fs'
import {developmentPorts} from './dev-ports.ts'

// 2026/11/03：开发服务器仅绑定本机，用 HTTPS 同源代理访问后端，生产只发布静态产物。
export default defineConfig(({ command }) => {
  // 2026/11/04：开发监听与代理共用端口来源，生产静态构建不要求启动开发端口。
  const ports = command === 'serve' ? developmentPorts(readFileSync(
    new URL('../src/main/resources/application.properties', import.meta.url), 'utf8'), process.env) : undefined
  return {
    base: '/console/',
    plugins: [vue(), basicSsl()],
    server: ports ? {
      port: ports.frontend,
      strictPort: true,
      proxy: {
        '/api/': {
          target: `http://127.0.0.1:${ports.backend}`,
          changeOrigin: false,
          headers: { 'X-Forwarded-Proto': 'https', 'X-Forwarded-Host': ports.authority },
        },
        // 2026/11/04：本机 WSS 在 Vite 终止 TLS，再向后端 HTTP 端口转发握手，保留机器认证头。
        '/ws/': {
          // 2026/11/05：独立 WebSocket 监听不改变普通 API 的代理目标。
          target: `ws://127.0.0.1:${ports.websocket}`,
          ws: true,
          changeOrigin: false,
          headers: { 'X-Forwarded-Proto': 'https', 'X-Forwarded-Host': ports.authority },
        },
      },
    } : undefined,
    build: {
      outDir: '../build/generated/web/console',
      emptyOutDir: true,
      sourcemap: false,
    },
  }
})

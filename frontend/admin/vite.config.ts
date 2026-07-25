/// <reference types="vitest/config" />
import path from 'node:path'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(import.meta.dirname, './src'),
    },
  },
  test: {
    environment: 'jsdom',
    globals: false,
    setupFiles: ['./src/test-setup.ts'],
  },
  server: {
    // 5175는 api-admin의 CORS 허용 목록(app.admin-console.allowed-origins)에 들어 있는
    // 포트다(payment 5173, merchant 5174와 겹치지 않게 나눴다). strictPort가 없으면
    // 포트가 밀렸을 때 Origin이 달라져 세션 쿠키 요청이 전부 CORS로 막힌다 —
    // 원인이 드러나지 않으므로 차라리 기동에 실패시킨다.
    port: 5175,
    strictPort: true,
  },
})

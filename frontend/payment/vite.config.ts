/// <reference types="vitest/config" />
import path from 'node:path'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    // shadcn/ui가 생성하는 코드가 `@/lib/utils` 형태로 import한다.
    // tsconfig의 paths는 타입 검사용이라 번들러에는 따로 알려줘야 한다.
    alias: {
      '@': path.resolve(import.meta.dirname, './src'),
    },
  },
  test: {
    environment: 'jsdom',
    globals: false,
  },
  server: {
    // 5173은 api-payment의 CORS 허용 목록(app.checkout.allowed-origins)에 들어 있는
    // 포트다. strictPort가 없으면 5173이 이미 쓰이고 있을 때 Vite가 조용히 5174로
    // 옮겨 가는데, 그러면 Origin이 달라져 모든 요청이 CORS로 막힌다 — 원인이
    // 드러나지 않으므로 포트를 못 잡으면 차라리 실패시킨다.
    port: 5173,
    strictPort: true,
  },
})

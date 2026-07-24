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
    // jest-dom 매처를 등록한다. 이게 없으면 패키지가 설치돼 있어도 매처를 쓸 수 없다.
    setupFiles: ['./src/test-setup.ts'],
  },
  server: {
    // 5174는 api-merchant의 CORS 허용 목록(app.merchant-console.allowed-origins)에 들어
    // 있는 포트다(payment가 5173을 쓰므로 겹치지 않게 5174로 나눴다). strictPort가 없으면
    // 포트가 이미 쓰일 때 Vite가 조용히 다른 포트로 옮겨 가는데, 그러면 Origin이 달라져
    // 세션 쿠키 요청이 전부 CORS로 막힌다 — 원인이 드러나지 않으므로 차라리 실패시킨다.
    port: 5174,
    strictPort: true,
  },
})

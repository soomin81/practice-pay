/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
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

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
    // 5173은 api-payment의 CORS 허용 목록(app.checkout.allowed-origins)에 들어 있는
    // 포트다. strictPort가 없으면 5173이 이미 쓰이고 있을 때 Vite가 조용히 5174로
    // 옮겨 가는데, 그러면 Origin이 달라져 모든 요청이 CORS로 막힌다 — 원인이
    // 드러나지 않으므로 포트를 못 잡으면 차라리 실패시킨다.
    port: 5173,
    strictPort: true,
    proxy: {
      // **DEV "테스트 결제 생성" 버튼 전용 우회로다.**
      //
      // 백엔드는 CORS를 `/checkout/**`에만 등록한다. 그건 실수가 아니라 의도된 보안
      // 경계다 — 앱 전체에 걸면 API Key로 보호되는 `POST /api/v1/payments`가 브라우저
      // 호출 표면이 된다(docs/architecture/checkout-api.md의 2.1). 그래서 DEV 버튼이
      // 그 API를 브라우저에서 직접 부르면 preflight에 Access-Control-Allow-Origin이
      // 없어 "Failed to fetch"로 막힌다 — 실제로 이 버튼은 그동안 브라우저에서 한 번도
      // 동작한 적이 없었다.
      //
      // 프록시를 거치면 브라우저에게는 5173 **동일 출처** 요청이라 CORS가 아예 발생하지
      // 않는다. **백엔드 경계를 넓히지 않고 푸는 것이 핵심이다** — 이 프록시는 Vite 개발
      // 서버에만 있고 프로덕션 번들에는 존재하지 않는다.
      //
      // 고객 대면 체크아웃 호출(`/checkout/**`)은 일부러 프록시를 타지 않는다. 그쪽은
      // 운영에서도 교차 출처라, 개발 중에도 진짜 CORS를 그대로 겪어야 설정이 깨진 것을
      // 미리 발견한다.
      '/api/v1': {
        target: 'http://localhost:8081',
      },
    },
  },
})

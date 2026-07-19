/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// 이 프로젝트는 개발 서버를 Docker 컨테이너에서 돌린다(frontend/compose.yaml) —
// 이 머신에 호스트 Node가 없기 때문이다. 그래서 기본값으로는 동작하지 않는 설정이
// 둘 있다.
export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    globals: false,
  },
  server: {
    // 컨테이너 내부 localhost가 아니라 모든 인터페이스에 바인딩해야 호스트
    // 브라우저에서 접속된다. compose의 command에도 --host를 주지만, 호스트에서
    // 직접 `npm run dev`를 돌리는 경우와 무관하게 여기서도 고정해 둔다.
    host: true,
    port: 5173,
    watch: {
      // Docker Desktop의 Windows 바인드 마운트는 inotify를 전달하지 않아서,
      // 폴링을 켜지 않으면 파일을 저장해도 HMR이 반응하지 않는다.
      usePolling: true,
      interval: 300,
    },
  },
})

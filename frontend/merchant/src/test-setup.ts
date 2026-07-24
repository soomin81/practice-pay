/**
 * Vitest 전역 설정(`vite.config.ts`의 `test.setupFiles`) — payment 앱의 test-setup.ts와
 * 같은 이유·같은 방식이다.
 *
 * jest-dom 매처를 Vitest의 `expect`에 등록한다(`/vitest` 진입점을 써야 한다 — 기본
 * 진입점은 Jest 전역 `expect`를 가정한다). 없으면 `toBeInTheDocument()` 같은 매처를
 * 못 써서 약한 단언으로 우회하게 된다.
 */
import '@testing-library/jest-dom/vitest'
import { afterEach } from 'vitest'
import { cleanup } from '@testing-library/react'

/**
 * 렌더한 DOM을 테스트마다 치운다. `globals: false`라 Testing Library의 자동 cleanup
 * 경로를 타지 않으므로 직접 등록한다(payment의 test-setup.ts와 같은 이유).
 */
afterEach(cleanup)

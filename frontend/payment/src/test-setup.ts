/**
 * Vitest 전역 설정(`vite.config.ts`의 `test.setupFiles`).
 *
 * jest-dom 매처를 Vitest의 `expect`에 등록한다 — `toBeInTheDocument()`,
 * `toHaveTextContent()`처럼 DOM을 상대로 의도가 드러나는 단언을 쓰기 위해서다.
 * 이게 없으면 `toBeTruthy()` 같은 약한 단언으로 우회하게 되는데, 그러면 무엇이
 * 깨졌는지가 실패 메시지에 남지 않는다.
 *
 * `/vitest` 진입점을 쓰는 것이 중요하다. 기본 진입점은 Jest의 전역 `expect`를
 * 가정해서 Vitest에서는 매처가 등록되지 않는다.
 */
import '@testing-library/jest-dom/vitest'

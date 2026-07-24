# 가맹점 콘솔 API 설계

가맹점 운영자용 콘솔(브라우저 SPA, `frontend/merchant`)이 호출하는 API 계약이다.
`apps:api-merchant`(:8083)가 구현하고, 계정·API Key의 도메인 규칙 원문은
[identity-access-api-key.md](identity-access-api-key.md)와 ADR-006에 있다.

> **이 문서는 contract-after다.** [checkout-api.md](checkout-api.md)가 구현보다 먼저 쓴
> contract-first 문서인 것과 달리, 여기 엔드포인트들은 이미 구현돼 있었고 이 문서는
> **브라우저 대면 계약(인증·CSRF·CORS·오류)**을 프론트엔드 작업 기준으로 정리한 것이다.
> 계약을 바꿀 때는 이 문서와 구현·OpenAPI 문서화 테스트를 같은 변경에서 함께 고친다.

## 1. 인증 모델 — 세션 쿠키

`payment`(쿠키 없는 API Key/무인증)와 **결정적으로 다르다.** 콘솔은 사람이 로그인하는
화면이라 세션 쿠키(`JSESSIONID`, HttpOnly)로 인증한다.

- 로그인 성공 시 서버가 세션 쿠키를 `Set-Cookie`로 내린다. 이후 요청은 브라우저가
  쿠키를 자동으로 실어 보낸다(`fetch(..., { credentials: 'include' })`).
- **세션 복원**: 프론트는 로그인 응답을 쿠키 말고는 저장하지 않는다. 새로고침 후
  `GET /merchant/me`로 현재 사용자를 복원한다 — 401이면 "로그아웃 상태"로 해석한다.
- **미인증은 401이다**(403 아님). 403은 CSRF 실패/권한 부족과 겹치므로, 프론트가
  "로그인 필요"를 분명히 구분하도록 엔트리포인트를 401로 고정했다.

## 2. CSRF — XSRF-TOKEN 쿠키 ↔ X-XSRF-TOKEN 헤더

세션 쿠키 인증이라 CSRF 방어가 필요하다. Spring Security 6의 SPA 표준 레시피를 쓴다.

- 서버는 `XSRF-TOKEN` 쿠키(HttpOnly 아님)를 내린다. 안전한 GET(예: `GET /merchant/me`)
  응답에 실려 온다.
- 프론트는 **상태 변경 요청(POST/DELETE)**에 그 쿠키 값을 `X-XSRF-TOKEN` 헤더로
  되돌려 실어야 한다. 없거나 틀리면 **403**이다.
- GET(안전한 조회)에는 토큰이 필요 없다.
- **예외: `POST /merchant/account-invitations/accept`는 CSRF 대상이 아니다.** 비인증
  공개 경로이고 자격증명이 세션 쿠키가 아니라 본문의 초대 Token 자체라, CSRF가
  막으려는 상황이 성립하지 않는다(이메일 링크로 도달해 토큰을 미리 받아올 GET을 앞에
  둘 수도 없다).

## 3. CORS

콘솔은 프론트(`http://localhost:5174`)와 API(`:8083`)의 Origin이 달라 CORS가 걸린다.

- 세션 쿠키를 실어 보내므로 `allowCredentials = true`이고, 그 제약상 허용 Origin에
  와일드카드를 쓸 수 없다 — `app.merchant-console.allowed-origins`(환경변수
  `APP_MERCHANT_CONSOLE_ALLOWED_ORIGINS`, 쉼표 구분)로 정확히 나열한다.
- 허용 헤더에 `X-XSRF-TOKEN`이 포함된다(위 CSRF 헤더).
- **운영에서 콘솔이 다른 도메인에 있으면 세션/XSRF 쿠키에 `SameSite=None; Secure`가
  필요하다**(로컬은 localhost 동일 사이트라 `Lax`로 동작). 이건 쿠키 속성 배포 설정의
  몫이라 코드에 하드코딩하지 않는다.

## 4. 엔드포인트 (현재 슬라이스)

성공 응답의 필드 상세는 **생성된 OpenAPI 스펙**이 기준이다(`api-merchant`의 `openapi3`
태스크 → 프론트 `npm run gen:api`). 오류 응답은 스펙에 없다(MockMvc가 오류 디스패치를
재현하지 못해 뺐다 — checkout-api.md 5절과 같은 한계). CSRF 열은 상태 변경 요청에
`X-XSRF-TOKEN`이 필요한지다.

| 메서드·경로 | 인증/권한 | CSRF | 성공 | 주요 오류 |
|---|---|---|---|---|
| `POST /merchant/login` | 공개 | 필요 | 200 신원 + 세션 쿠키 | 400 검증, 401 자격증명 불일치/잠금 |
| `GET /merchant/me` | 세션 | — | 200 신원(+XSRF 쿠키 발급) | 401 미인증 |
| `POST /merchant/logout` | 세션 | 필요 | 204 | 401 미인증 |
| `GET /merchant/api-keys` | OWNER/ADMIN | — | 200 목록 | 401, 403(VIEWER) |
| `POST /merchant/api-keys` | OWNER/ADMIN | 필요 | 201 발급(rawApiKey 1회) | 400 검증, 401, 403 |
| `DELETE /merchant/api-keys/{id}` | OWNER/ADMIN | 필요 | 200 폐기 | 401, 403, 404 없음 |

- **`rawApiKey`는 발급 응답에서만 원문으로 보인다**(6.4). 목록에는 Secret 관련 필드가
  아예 담기지 않는다.
- **`VIEWER`의 API Key 목록 조회는 막는다**(403). `docs/`의 "6.6"이 VIEWER를 "제한적
  또는 불가"로 남겼는데, OWNER/ADMIN 전용 게이트를 그대로 적용했다.

## 5. 다음 슬라이스로 미룬 것

이번 프론트 슬라이스(로그인 → API Key 관리)에는 없지만 백엔드에 이미 구현돼 있다:

- `POST /merchant/merchant-users` — 하위 계정 발급(OWNER/ADMIN). CSRF 필요.
- `POST /merchant/account-invitations/accept` — 초대 수락(공개, CSRF 예외 — 2절).

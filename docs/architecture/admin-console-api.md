# 내부 운영자 콘솔 API 설계

PG 내부 운영자용 콘솔(브라우저 SPA, `frontend/admin`)이 호출하는 API 계약이다.
`apps:api-admin`(:8082)이 구현하고, 계정·권한의 도메인 규칙 원문은
[identity-access-api-key.md](identity-access-api-key.md)와 ADR-006에 있다.

> **contract-after 문서다** — 엔드포인트들은 이미 구현돼 있었고, 이 문서는 프론트엔드
> 작업의 기준이 되는 **브라우저 대면 계약**(인증·CSRF·CORS·오류)을 정리한 것이다.
> [merchant-console-api.md](merchant-console-api.md)와 같은 성격·같은 구성이다.

## 1. 인증 모델 — 세션 쿠키

가맹점 콘솔과 **같은 방식**이다(자세한 배경은 그 문서 1절):

- 로그인 성공 시 세션 쿠키(`JSESSIONID`, HttpOnly)를 내리고, 이후 요청은 브라우저가
  자동으로 실어 보낸다(`credentials: 'include'`).
- **세션 복원**은 `GET /admin/me` — 401이면 "로그아웃 상태"로 해석한다.
- **미인증은 401이다**(403 아님). 403은 CSRF 실패/권한 부족과 겹쳐서 구분이 필요하다.

**가맹점 콘솔과 다른 점**: 로그인에 `merchantCode`가 없다. 내부 운영자는 특정 가맹점에
속하지 않고 `login_id`가 전 시스템에서 유일하다.

## 2. CSRF — XSRF-TOKEN 쿠키 ↔ X-XSRF-TOKEN 헤더

가맹점 콘솔과 **완전히 같은 레시피**다(Spring Security 6 SPA 방식, 지연 토큰 로딩을
깨우는 `CsrfCookieFilter` 포함). 상태 변경 요청(POST)에 헤더가 없거나 틀리면 **403**이다.

- **예외: `POST /admin/account-invitations/accept`** — 비인증 공개 경로이고 자격증명이
  세션 쿠키가 아니라 본문의 초대 Token 자체라 CSRF가 막을 대상이 아니다.

## 3. CORS

`app.admin-console.allowed-origins`(환경변수 `APP_ADMIN_CONSOLE_ALLOWED_ORIGINS`,
기본 `http://localhost:5175`)로 정확한 Origin만 허용한다. 세션 쿠키를 실어 보내므로
`allowCredentials = true`이고 와일드카드를 쓸 수 없다. 허용 헤더에 `X-XSRF-TOKEN`이 포함된다.

## 4. 엔드포인트

성공 응답 필드는 생성된 OpenAPI 스펙이 기준이다(`:apps:api-admin:openapi3` → 프론트
`npm run gen:api`). 오류 응답은 스펙에 없다(MockMvc가 오류 디스패치를 재현하지 못한다).

| 메서드·경로 | 인증/권한 | CSRF | 성공 | 주요 오류 |
|---|---|---|---|---|
| `POST /admin/login` | 공개 | 필요 | 200 신원 + 세션 쿠키 | 400 검증, 401 자격증명 불일치/잠금 |
| `GET /admin/me` | 세션 | — | 200 신원(+XSRF 쿠키 발급) | 401 미인증 |
| `POST /admin/logout` | 세션 | 필요 | 204 | 401 |
| `GET /admin/merchants` | **내부 운영자 전원**(VIEWER 포함) | — | 200 목록 | 401 |
| `POST /admin/merchants` | SUPER_ADMIN/OPERATOR | 필요 | 201 가맹점 + OWNER 초대 Token(1회) | 400 검증, 401, 403, 409 중복 |
| `POST /admin/internal-users` | **SUPER_ADMIN만** | 필요 | 201 계정 + 초대 Token(1회) | 400, 401, 403, 409 중복 |
| `POST /admin/account-invitations/accept` | **공개** | **불필요**(2절) | 200 활성화 | 400 유효하지 않거나 만료된 초대 |

- **`GET`과 `POST`의 권한이 다르다** — `SecurityConfig`가 `/admin/merchants`에 대해
  `HttpMethod.POST`로만 역할을 요구한다. `VIEWER`가 "조회 전용"이라는 정의를 지키기 위한
  의도적인 메서드 스코핑이고, `bootRun`으로 `GET` 200 / `POST` 403을 확인했다.
- **초대 Token은 발급 응답에서만 원문으로 보인다**(DB에는 Hash만 남는다).

## 5. 가맹점 등록이 만드는 초대 링크는 **가맹점 콘솔**을 가리킨다

이 콘솔에서 가장 헷갈리기 쉬운 지점이다. `POST /admin/merchants`가 돌려주는
`invitationToken`은 **새 가맹점 OWNER의 것**이고, 그 사람이 계정을 활성화할 곳은
내부 운영자 콘솔이 아니라 **가맹점 콘솔**이다:

```
{가맹점 콘솔 Origin}/accept-invitation?token={invitationToken}
```

그래서 `frontend/admin`은 `VITE_MERCHANT_CONSOLE_URL`(기본 `http://localhost:5174`)로
링크를 만든다 — 자기 `window.location.origin`을 쓰면 상대가 열 수 없는 링크가 된다.
수락 요청 자체도 `api-admin`이 아니라 **`api-merchant`의**
`POST /merchant/account-invitations/accept`로 간다.

> 이 흐름은 두 앱이 `app.invitation-token.pepper`를 같은 값으로 갖고 있어야 동작한다
> (`backend/CLAUDE.md`의 "설정과 비밀값"). 어긋나면 "유효하지 않은 초대"로만 보여
> 추적이 어렵다 — 실물 검증에서 이 왕복을 직접 확인했다.

전체 흐름:

```
내부 운영자 로그인 → 가맹점 등록 → OWNER 초대 링크 전달
→ (가맹점 콘솔) OWNER가 비밀번호 설정 → 로그인 → API Key 발급
```

## 6. 다음 슬라이스로 미룬 것

- **내부 직원 계정 발급 화면**(`POST /admin/internal-users`, SUPER_ADMIN 전용) — 백엔드는
  이미 구현돼 있다.
- **내부 운영자의 가맹점 계정 관리** — 그때 merchant 콘솔에 구현해 둔 "최소 하나의 활성
  OWNER를 유지한다" 불변식이 실제로 트리거되는 첫 경로가 된다
  ([merchant-console-api.md](merchant-console-api.md)의 6절 5번).

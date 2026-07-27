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
| `GET /admin/internal-users` | **SUPER_ADMIN만** | — | 200 명부 | 401, 403 |
| `GET /admin/login-audit` | **SUPER_ADMIN만** | — | 200 로그인 감사(최신순) | 401, 403 |
| `POST /admin/internal-users` | **SUPER_ADMIN만** | 필요 | 201 계정 + 초대 Token(1회) | **400 `role=SUPER_ADMIN`**, 400 검증, 401, 403, 409 중복 |
| `POST /admin/internal-users/{id}/suspend` | **SUPER_ADMIN만** | 필요 | 200 상태(SUSPENDED) | 401, 403(자기 자신), 404, 409(마지막 SUPER_ADMIN·잘못된 전이) |
| `POST /admin/internal-users/{id}/reactivate` | **SUPER_ADMIN만** | 필요 | 200 상태(ACTIVE) | 401, 403(자기 자신), 404, 409(잘못된 전이) |
| `POST /admin/internal-users/{id}/terminate` | **SUPER_ADMIN만** | 필요 | 200 상태(TERMINATED) | 401, 403(자기 자신), 404, 409(마지막 SUPER_ADMIN·잘못된 전이) |
| `POST /admin/internal-users/{id}/role` | **SUPER_ADMIN만** | 필요 | 200 역할 | **400 `role=SUPER_ADMIN`**, 401, 403(자기 자신), 404, 409(마지막 SUPER_ADMIN 강등·종료된 계정) |
| `GET /admin/merchants/{merchantId}/users` | **내부 운영자 전원**(VIEWER 포함) | — | 200 명부 | 401 |
| `POST /admin/merchants/{merchantId}/users/{id}/suspend` | SUPER_ADMIN/OPERATOR | 필요 | 200 상태(SUSPENDED) | 401, 403, 404, 409(마지막 OWNER·잘못된 전이) |
| `POST /admin/merchants/{merchantId}/users/{id}/reactivate` | SUPER_ADMIN/OPERATOR | 필요 | 200 상태(ACTIVE) | 401, 403, 404, 409(잘못된 전이) |
| `POST /admin/merchants/{merchantId}/users/{id}/terminate` | SUPER_ADMIN/OPERATOR | 필요 | 200 상태(TERMINATED) | 401, 403, 404, 409(마지막 OWNER·잘못된 전이) |
| `POST /admin/merchants/{merchantId}/users/{id}/role` | SUPER_ADMIN/OPERATOR | 필요 | 200 역할 | **400 `role=OWNER`**, 401, 403, 404, 409(마지막 OWNER 강등·종료된 계정) |
| `POST /admin/account-invitations/accept` | **공개** | **불필요**(2절) | 200 활성화 | 400 유효하지 않거나 만료된 초대 |

- **두 경로의 메서드 스코핑이 정반대다 — 의도적이다.**
  - `/admin/merchants`는 `HttpMethod.POST`로만 역할을 요구해서 **`GET`이 `VIEWER`에게
    열린다**(`VIEWER` = "조회 전용" 정의를 지킨다).
  - `/admin/internal-users`는 메서드로 좁히지 않아 **`GET`도 `SUPER_ADMIN` 전용이다.**
    내부 직원 명부에는 직원 이메일·마지막 로그인·누가 `SUPER_ADMIN`인지가 담기고, 계정
    관리 자체가 `SUPER_ADMIN`의 영역이기 때문이다("3.3").
  - `bootRun`으로 같은 `OPERATOR` 세션이 `GET /admin/merchants` 200,
    `GET /admin/internal-users` 403을 받는 것을 확인했다.
- **초대 Token은 발급 응답에서만 원문으로 보인다**(DB에는 Hash만 남는다).
- **`SUPER_ADMIN`은 초대로 만들 수 없다**(400) — "3.3"이 최초 SUPER_ADMIN을 Bootstrap
  경로로만 만들도록 규정한다. 도메인 `InternalUser.invite`가 막으므로 API를 직접 호출해도
  통하지 않는다(가맹점 쪽에서 `OWNER`를 막는 것과 같은 방식).
- **계정 상태·역할 관리(`/{id}/suspend|reactivate|terminate|role`)는 전부 `SUPER_ADMIN`
  전용이다.** `SecurityConfig`가 `/admin/internal-users/**` 와일드카드로 하위 경로까지
  `SUPER_ADMIN`으로 잠근다(가맹점 콘솔이 계정 관리 인가를 Use Case에서 동적으로 확인하는
  것과 달리, admin은 정적 규칙에 맡긴다 — 그래서 요청자 식별자는 인가가 아니라 **자기
  자신 차단**에만 쓴다).
  - **자기 자신은 대상으로 삼을 수 없다**(403) — 스스로를 정지·종료·강등하면 복구
    수단이 사라진다.
  - **`role`은 `OPERATOR`|`VIEWER`여야 한다** — `SUPER_ADMIN`으로 승격하면 400이다(발급과
    같은 제약, 도메인 `InternalUser.changeRole`가 막는다).
  - **"최소 하나의 활성 SUPER_ADMIN을 유지한다"**("3.3") — 마지막 활성 SUPER_ADMIN을
    정지·종료·강등하려는 요청은 409다. 사라지면 아무도 내부 계정을 발급할 수 없는 상태로
    굳는다(복구는 Bootstrap 같은 운영 절차뿐이다). 가맹점 쪽 "최소 하나의 활성 OWNER"와
    같은 성격의 불변식이다.
  - **되돌릴 수 없는 상태 전이는 도메인이 막는다**(예: 종료된 계정 재개·역할 변경 → 409).
- **가맹점 계정 관리(`/admin/merchants/{id}/users/**`)는 조회와 변경의 인가가 갈린다.**
  조회(`GET`)는 인증된 내부 사용자 전원(VIEWER 포함 — `GET /admin/merchants`와 같은
  스코핑), 변경(`POST`)은 `SUPER_ADMIN`/`OPERATOR`다(가맹점 등록 `POST /admin/merchants`와
  같은 역할 집합, `identity-access-api-key.md`의 4.6). `SecurityConfig`가 `HttpMethod.POST`로
  `/admin/merchants/**`를 좁혀 이 갈림을 만든다.
  - `role`은 `ADMIN`|`VIEWER`여야 한다 — `OWNER`로 승격하면 400(도메인이 막는다).
  - **"가맹점에는 최소 하나의 활성 OWNER"** — 마지막 활성 OWNER를 정지·종료·강등하면 409다.
    **이 API가 그 불변식이 실제로 트리거되는 첫 경로다**(가맹점 콘솔 경로에서는 자기 자신
    차단·ADMIN 제한 때문에 도달할 수 없었다).
  - 대상이 경로의 가맹점 소속이 아니면 404다(남의 가맹점 사용자의 존재 여부를 숨긴다).

## 5. 초대 링크가 **두 종류**다 — 가리키는 콘솔이 다르다

이 콘솔에서 가장 헷갈리기 쉬운 지점이다. admin 콘솔은 초대를 두 가지 만들고, **활성화
화면과 수락 API가 서로 다른 앱에 있다**:

| 발급 | 대상 | 활성화 화면 | 수락 API |
|---|---|---|---|
| `POST /admin/merchants` | 새 가맹점 **OWNER** | **가맹점 콘솔**(`VITE_MERCHANT_CONSOLE_URL`) | `api-merchant`의 `POST /merchant/account-invitations/accept` |
| `POST /admin/internal-users` | **내부 직원** | **admin 콘솔 자신**(`window.location.origin`) | `api-admin`의 `POST /admin/account-invitations/accept` |

둘 다 링크 형식은 같다:

```
{해당 콘솔 Origin}/accept-invitation?token={invitationToken}
```

**바꿔 쓰면 화면상으로는 멀쩡한데 상대가 열 수 없는 링크가 된다.** 그래서
`frontend/admin`은 `console/format.ts`에 `merchantInvitationUrlFor`/`internalInvitationUrlFor`
두 함수를 나란히 두고, 노출 컴포넌트(`InvitationReveal`)가 **링크 생성 함수를 주입받아**
호출부에서 어느 콘솔인지 드러나게 한다. 프론트 테스트가 "둘이 서로 다른 origin을 가리킨다"를
회귀로 지킨다.

> 가맹점 OWNER 흐름은 두 앱이 `app.invitation-token.pepper`를 같은 값으로 갖고 있어야
> 동작한다(`backend/CLAUDE.md`의 "설정과 비밀값") — 실물 검증에서 이 왕복을 확인했다.
> 내부 직원 흐름은 발급·수락이 같은 앱이라 그 제약이 없다.

> 이 흐름은 두 앱이 `app.invitation-token.pepper`를 같은 값으로 갖고 있어야 동작한다
> (`backend/CLAUDE.md`의 "설정과 비밀값"). 어긋나면 "유효하지 않은 초대"로만 보여
> 추적이 어렵다 — 실물 검증에서 이 왕복을 직접 확인했다.

전체 흐름:

```
내부 운영자 로그인 → 가맹점 등록 → OWNER 초대 링크 전달
→ (가맹점 콘솔) OWNER가 비밀번호 설정 → 로그인 → API Key 발급
```

## 6. 다음 슬라이스로 미룬 것

- 지금까지 이 문서가 미뤄 둔 항목(내부 직원 계정 관리, 내부 운영자의 가맹점 계정 관리,
  만료 초대 정리 배치)은 전부 구현됐고, **내부 운영자 로그인 감사 조회**(`GET /admin/login-audit`,
  SUPER_ADMIN 전용)도 붙었다. 남은 후속 후보는 **가맹점 로그인·API Key 사용 감사**(같은 감사
  인프라를 확장 — [identity-access-api-key.md](identity-access-api-key.md)의 9절 후속)다.

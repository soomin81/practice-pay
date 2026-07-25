# 스테이블코인 결제 프로젝트 문서

## 가이드 (실행 절차)

설계 기준이 아니라 **직접 돌려보기 위한 절차서**다.

- [MetaMask로 테스트넷 USDC 준비하고 결제 흘려보기](guides/testnet-wallet-setup.md)

## 도메인

- [도메인 용어 사전](domain/glossary.md)
- [도메인 모델](domain/domain-model.md)
- [상태 전이 정책](domain/state-transitions.md)

## 아키텍처

- [MVP 범위](architecture/mvp-scope.md)
- [MySQL·jOOQ 영속성 원칙](architecture/persistence-jooq.md)
- [계정·권한·API Key 설계](architecture/identity-access-api-key.md)
- [Hosted Checkout API 설계](architecture/checkout-api.md)
- [가맹점 콘솔 API 설계](architecture/merchant-console-api.md)
- [내부 운영자 콘솔 API 설계](architecture/admin-console-api.md)

## 데이터베이스

- [DB 상세 설계](database/database-design.md)
- [MySQL 스키마 (Flyway migrations)](../backend/db-core/src/main/resources/db/migration/)

## ADR

- [ADR-001 MVP 범위](decisions/ADR-001-mvp-scope.md)
- [ADR-002 MySQL과 jOOQ](decisions/ADR-002-mysql-jooq.md)
- [ADR-003 Hosted Checkout](decisions/ADR-003-hosted-checkout.md)
- [ADR-004 Fake Exchange](decisions/ADR-004-fake-exchange.md)
- [ADR-005 정산 경계](decisions/ADR-005-settlement-boundary.md)
- [ADR-006 관리자 계정과 가맹점 API Key 분리](decisions/ADR-006-identity-api-key-separation.md)

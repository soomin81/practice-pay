# 스테이블코인 결제 프로젝트 문서

## 도메인

- [도메인 용어 사전](domain/glossary.md)
- [도메인 모델](domain/domain-model.md)
- [상태 전이 정책](domain/state-transitions.md)

## 아키텍처

- [MVP 범위](architecture/mvp-scope.md)
- [MySQL·jOOQ 영속성 원칙](architecture/persistence-jooq.md)

## 데이터베이스

- [DB 상세 설계](database/database-design.md)
- [MySQL 스키마 (Flyway migration)](../backend/db-core/src/main/resources/db/migration/V1__init_schema.sql)

## ADR

- [ADR-001 MVP 범위](decisions/ADR-001-mvp-scope.md)
- [ADR-002 MySQL과 jOOQ](decisions/ADR-002-mysql-jooq.md)
- [ADR-003 Hosted Checkout](decisions/ADR-003-hosted-checkout.md)
- [ADR-004 Fake Exchange](decisions/ADR-004-fake-exchange.md)
- [ADR-005 정산 경계](decisions/ADR-005-settlement-boundary.md)

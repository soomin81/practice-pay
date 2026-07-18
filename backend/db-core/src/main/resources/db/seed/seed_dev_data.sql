-- ============================================================
-- 로컬 개발용 시드 데이터 (1/2) — 가맹점
--
-- 이 파일은 Flyway 마이그레이션이 아니다. `db/migration/`이 아니라 여기 있는
-- 이유는, 운영에 Flyway를 붙였을 때(spring-boot-starter-flyway의 기본 위치는
-- classpath:db/migration이다) **설정을 아무것도 하지 않아도 자동으로 제외**되게
-- 하기 위해서다 — 개발 계정이 운영에 실리는 사고는 되돌리기 어려우니, 실수해도
-- 안전한 쪽을 기본값으로 뒀다(backend/CLAUDE.md의 "Database / jOOQ 코드 생성" 참고).
--
-- 적용은 스키마 마이그레이션을 모두 적용한 뒤 수동으로 한다:
--   docker exec -i backend-mysql-1 mysql --default-character-set=utf8mb4 \
--     -uroot -pverysecret stablecoin_payment < db-core/src/main/resources/db/seed/seed_dev_data.sql
--
-- 순서: 이 파일을 먼저 적용한다 — seed_dev_identity_data.sql이 여기서 만드는
-- mrc_test_001 가맹점을 참조한다.
--
-- 재적용은 지원하지 않는다(순수 INSERT라 중복 키로 실패한다) — 다시 심으려면
-- DB를 재생성한다.
-- ============================================================

INSERT INTO merchant (
    merchant_id,
    merchant_code,
    merchant_name,
    merchant_status,
    webhook_url,
    created_at,
    updated_at,
    version
)
VALUES (
    'mrc_test_001',
    'TEST_MERCHANT',
    '테스트 가맹점',
    'ACTIVE',
    'http://localhost:8081/webhooks/stablecoin',
    UTC_TIMESTAMP(6),
    UTC_TIMESTAMP(6),
    0
);

-- ============================================================
-- 로컬 개발용 시드 데이터 (2/2) — Identity & API Key
--
-- 이 파일은 Flyway 마이그레이션이 아니다(같은 폴더의 seed_dev_data.sql 헤더 참고 —
-- 운영에서 자동으로 제외되도록 db/migration/ 밖에 둔다).
--
-- 적용 순서: seed_dev_data.sql을 먼저 적용해야 한다 — 그 파일이 만드는
-- mrc_test_001 가맹점에 계정과 API Key를 얹는다.
--   docker exec -i backend-mysql-1 mysql --default-character-set=utf8mb4 \
--     -uroot -pverysecret stablecoin_payment < db-core/src/main/resources/db/seed/seed_dev_identity_data.sql
--
-- 전부 학습/로컬 개발 전용 값이다 — 운영 DB에는 절대 적용하지 않는다.
--
-- 로그인 비밀번호(둘 다 동일): DevPassword123!
-- API Key(Authorization: Bearer): sk_test_devkey01_dev-secret-value
-- (해시는 각각 BCryptPasswordEncoder / HmacApiKeySecretHasher로 미리 계산해
-- 넣은 값이다 — apps:api-admin/api-merchant/api-payment의 application.yaml에
-- 있는 것과 같은 알고리즘·Pepper를 썼다.)
-- ============================================================

-- 내부 운영자(SUPER_ADMIN) — apps:api-admin의 POST /admin/login용
INSERT INTO internal_user (
    internal_user_id,
    login_id,
    email,
    user_name,
    password_hash,
    user_status,
    role_code,
    failed_login_count,
    password_changed_at,
    activated_at,
    created_at,
    updated_at,
    version
)
VALUES (
    'iu_dev_001',
    'dev-admin',
    'dev-admin@example.com',
    '개발용 관리자',
    '$2a$10$yoaC4KKDpZ/x.Cbym/Tf2uqUiH6nNm2uWx5vljrkFikb7qB.hdVPG',
    'ACTIVE',
    'SUPER_ADMIN',
    0,
    UTC_TIMESTAMP(6),
    UTC_TIMESTAMP(6),
    UTC_TIMESTAMP(6),
    UTC_TIMESTAMP(6),
    0
);

SET @merchant_seq = (SELECT merchant_seq FROM merchant WHERE merchant_id = 'mrc_test_001');
SET @internal_user_seq = (SELECT internal_user_seq FROM internal_user WHERE login_id = 'dev-admin');

-- 가맹점 관리자(OWNER) — apps:api-merchant의 POST /merchant/login용
-- (merchantCode: TEST_MERCHANT)
INSERT INTO merchant_user (
    merchant_user_id,
    merchant_seq,
    login_id,
    email,
    user_name,
    password_hash,
    user_status,
    role_code,
    failed_login_count,
    password_changed_at,
    activated_at,
    invited_by_internal_user_seq,
    created_at,
    updated_at,
    version
)
VALUES (
    'mu_dev_001',
    @merchant_seq,
    'dev-owner',
    'dev-owner@example.com',
    '개발용 오너',
    '$2a$10$yoaC4KKDpZ/x.Cbym/Tf2uqUiH6nNm2uWx5vljrkFikb7qB.hdVPG',
    'ACTIVE',
    'OWNER',
    0,
    UTC_TIMESTAMP(6),
    UTC_TIMESTAMP(6),
    @internal_user_seq,
    UTC_TIMESTAMP(6),
    UTC_TIMESTAMP(6),
    0
);

SET @merchant_user_seq = (SELECT merchant_user_seq FROM merchant_user WHERE login_id = 'dev-owner');

-- 가맹점 API Key — apps:api-payment의 POST /api/v1/payments용
-- 원문: sk_test_devkey01_dev-secret-value
INSERT INTO merchant_api_key (
    merchant_api_key_id,
    merchant_seq,
    key_name,
    api_environment,
    key_prefix,
    secret_hash,
    hash_algorithm,
    api_key_status,
    created_by_merchant_user_seq,
    created_at,
    updated_at,
    version
)
VALUES (
    'mak_dev_001',
    @merchant_seq,
    '개발용 Key',
    'TEST',
    'sk_test_devkey01',
    'uGR759YFwswQxZU+Q0tII9d9MCiddDdBCuQQRhC3UKI=',
    'HMAC-SHA256',
    'ACTIVE',
    @merchant_user_seq,
    UTC_TIMESTAMP(6),
    UTC_TIMESTAMP(6),
    0
);

SET @merchant_api_key_seq = (SELECT merchant_api_key_seq FROM merchant_api_key WHERE key_prefix = 'sk_test_devkey01');

INSERT INTO merchant_api_key_scope (merchant_api_key_seq, scope_code, created_at)
VALUES
    (@merchant_api_key_seq, 'PAYMENT_CREATE', UTC_TIMESTAMP(6)),
    (@merchant_api_key_seq, 'PAYMENT_READ', UTC_TIMESTAMP(6));

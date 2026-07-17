-- ============================================================
-- Optional seed data for local development
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

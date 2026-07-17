-- ============================================================
-- Stablecoin Payment MVP Schema
-- Database: MySQL 8.x
-- Access Technology: jOOQ
-- Character Set: utf8mb4
-- Time Storage Policy: UTC
-- ============================================================

CREATE DATABASE IF NOT EXISTS stablecoin_payment
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE stablecoin_payment;

-- ============================================================
-- 1. merchant
-- ============================================================

CREATE TABLE merchant (
    merchant_seq BIGINT NOT NULL AUTO_INCREMENT,
    merchant_id VARCHAR(40) NOT NULL,
    merchant_code VARCHAR(50) NOT NULL,
    merchant_name VARCHAR(200) NOT NULL,
    merchant_status VARCHAR(30) NOT NULL,
    webhook_url VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT pk_merchant
        PRIMARY KEY (merchant_seq),

    CONSTRAINT uk_merchant_merchant_id
        UNIQUE (merchant_id),

    CONSTRAINT uk_merchant_merchant_code
        UNIQUE (merchant_code),

    CONSTRAINT ck_merchant_version
        CHECK (version >= 0),

    CONSTRAINT ck_merchant_status
        CHECK (
            merchant_status IN (
                'ACTIVE',
                'SUSPENDED',
                'TERMINATED'
            )
        )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '가맹점';

CREATE INDEX idx_merchant_status
    ON merchant (merchant_status);


-- ============================================================
-- 2. payment
-- ============================================================

CREATE TABLE payment (
    payment_seq BIGINT NOT NULL AUTO_INCREMENT,
    payment_id VARCHAR(40) NOT NULL,
    merchant_seq BIGINT NOT NULL,
    merchant_order_id VARCHAR(100) NOT NULL,
    order_name VARCHAR(200) NOT NULL,
    order_currency VARCHAR(10) NOT NULL,
    order_amount BIGINT NOT NULL,
    payment_asset_code VARCHAR(20) NOT NULL,
    payment_amount_minor BIGINT NOT NULL,
    token_decimals SMALLINT NOT NULL,
    network_code VARCHAR(50) NOT NULL,
    receiving_wallet_address VARCHAR(100) NOT NULL,
    customer_wallet_address VARCHAR(100) NULL,
    payment_status VARCHAR(30) NOT NULL,
    failure_code VARCHAR(50) NULL,
    failure_message VARCHAR(500) NULL,
    expires_at DATETIME(6) NOT NULL,
    paid_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT pk_payment
        PRIMARY KEY (payment_seq),

    CONSTRAINT uk_payment_payment_id
        UNIQUE (payment_id),

    CONSTRAINT uk_payment_merchant_order
        UNIQUE (merchant_seq, merchant_order_id),

    CONSTRAINT fk_payment_merchant
        FOREIGN KEY (merchant_seq)
        REFERENCES merchant (merchant_seq)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT ck_payment_order_amount
        CHECK (order_amount > 0),

    CONSTRAINT ck_payment_amount_minor
        CHECK (payment_amount_minor > 0),

    CONSTRAINT ck_payment_token_decimals
        CHECK (token_decimals >= 0),

    CONSTRAINT ck_payment_version
        CHECK (version >= 0),

    CONSTRAINT ck_payment_status
        CHECK (
            payment_status IN (
                'CREATED',
                'READY',
                'PROCESSING',
                'CONFIRMING',
                'SUCCEEDED',
                'EXPIRED',
                'FAILED'
            )
        ),

    CONSTRAINT ck_payment_paid_at
        CHECK (
            payment_status <> 'SUCCEEDED'
            OR paid_at IS NOT NULL
        )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '스테이블코인 결제';

CREATE INDEX idx_payment_merchant_created
    ON payment (merchant_seq, created_at);

CREATE INDEX idx_payment_status_updated
    ON payment (payment_status, updated_at);

CREATE INDEX idx_payment_status_expires
    ON payment (payment_status, expires_at);


-- ============================================================
-- 3. payment_quote
-- ============================================================

CREATE TABLE payment_quote (
    payment_quote_seq BIGINT NOT NULL AUTO_INCREMENT,
    payment_quote_id VARCHAR(40) NOT NULL,
    payment_seq BIGINT NOT NULL,
    market_provider_code VARCHAR(50) NOT NULL,
    base_asset_code VARCHAR(20) NOT NULL,
    quote_currency VARCHAR(10) NOT NULL,
    market_rate DECIMAL(24, 12) NOT NULL,
    applied_rate DECIMAL(24, 12) NOT NULL,
    spread_rate DECIMAL(12, 8) NOT NULL DEFAULT 0,
    order_amount BIGINT NOT NULL,
    payment_amount_minor BIGINT NOT NULL,
    quoted_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,

    CONSTRAINT pk_payment_quote
        PRIMARY KEY (payment_quote_seq),

    CONSTRAINT uk_payment_quote_id
        UNIQUE (payment_quote_id),

    CONSTRAINT uk_payment_quote_payment
        UNIQUE (payment_seq),

    CONSTRAINT fk_payment_quote_payment
        FOREIGN KEY (payment_seq)
        REFERENCES payment (payment_seq)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT ck_payment_quote_market_rate
        CHECK (market_rate > 0),

    CONSTRAINT ck_payment_quote_applied_rate
        CHECK (applied_rate > 0),

    CONSTRAINT ck_payment_quote_order_amount
        CHECK (order_amount > 0),

    CONSTRAINT ck_payment_quote_payment_amount
        CHECK (payment_amount_minor > 0),

    CONSTRAINT ck_payment_quote_expiration
        CHECK (quoted_at < expires_at)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '결제 환율 및 결제 금액 견적 스냅샷';


-- ============================================================
-- 4. checkout_session
-- ============================================================

CREATE TABLE checkout_session (
    checkout_session_seq BIGINT NOT NULL AUTO_INCREMENT,
    checkout_session_id VARCHAR(50) NOT NULL,
    payment_seq BIGINT NOT NULL,
    checkout_status VARCHAR(30) NOT NULL,
    success_url VARCHAR(1000) NOT NULL,
    cancel_url VARCHAR(1000) NULL,
    connected_wallet_address VARCHAR(100) NULL,
    opened_at DATETIME(6) NULL,
    wallet_connected_at DATETIME(6) NULL,
    payment_submitted_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT pk_checkout_session
        PRIMARY KEY (checkout_session_seq),

    CONSTRAINT uk_checkout_session_id
        UNIQUE (checkout_session_id),

    CONSTRAINT uk_checkout_session_payment
        UNIQUE (payment_seq),

    CONSTRAINT fk_checkout_session_payment
        FOREIGN KEY (payment_seq)
        REFERENCES payment (payment_seq)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT ck_checkout_session_version
        CHECK (version >= 0),

    CONSTRAINT ck_checkout_session_status
        CHECK (
            checkout_status IN (
                'CREATED',
                'OPEN',
                'WALLET_CONNECTED',
                'PAYMENT_SUBMITTED',
                'COMPLETED',
                'EXPIRED',
                'CANCELLED'
            )
        ),

    CONSTRAINT ck_checkout_session_completed_at
        CHECK (
            checkout_status <> 'COMPLETED'
            OR completed_at IS NOT NULL
        )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'PG Hosted Checkout 세션';

CREATE INDEX idx_checkout_status_expires
    ON checkout_session (checkout_status, expires_at);


-- ============================================================
-- 5. blockchain_transaction
-- ============================================================

CREATE TABLE blockchain_transaction (
    blockchain_transaction_seq BIGINT NOT NULL AUTO_INCREMENT,
    blockchain_transaction_id VARCHAR(50) NOT NULL,
    payment_seq BIGINT NOT NULL,
    transaction_type VARCHAR(30) NOT NULL,
    network_code VARCHAR(50) NOT NULL,
    chain_id BIGINT NOT NULL,
    transaction_hash VARCHAR(150) NOT NULL,
    from_address VARCHAR(100) NULL,
    to_address VARCHAR(100) NULL,
    token_contract_address VARCHAR(100) NULL,
    token_asset_code VARCHAR(20) NOT NULL,
    amount_minor BIGINT NULL,
    transaction_status VARCHAR(30) NOT NULL,
    block_number BIGINT NULL,
    confirmation_count INT NOT NULL DEFAULT 0,
    required_confirmation_count INT NOT NULL,
    failure_code VARCHAR(50) NULL,
    failure_message VARCHAR(500) NULL,
    submitted_at DATETIME(6) NOT NULL,
    detected_at DATETIME(6) NULL,
    confirmed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT pk_blockchain_transaction
        PRIMARY KEY (blockchain_transaction_seq),

    CONSTRAINT uk_blockchain_transaction_id
        UNIQUE (blockchain_transaction_id),

    CONSTRAINT uk_blockchain_network_hash
        UNIQUE (network_code, transaction_hash),

    CONSTRAINT uk_blockchain_payment_type
        UNIQUE (payment_seq, transaction_type),

    CONSTRAINT fk_blockchain_transaction_payment
        FOREIGN KEY (payment_seq)
        REFERENCES payment (payment_seq)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT ck_blockchain_transaction_type
        CHECK (
            transaction_type IN (
                'PAYMENT',
                'REFUND'
            )
        ),

    CONSTRAINT ck_blockchain_transaction_status
        CHECK (
            transaction_status IN (
                'SUBMITTED',
                'DETECTED',
                'CONFIRMING',
                'CONFIRMED',
                'FAILED',
                'REORGED'
            )
        ),

    CONSTRAINT ck_blockchain_confirmation_count
        CHECK (confirmation_count >= 0),

    CONSTRAINT ck_blockchain_required_confirmation_count
        CHECK (required_confirmation_count > 0),

    CONSTRAINT ck_blockchain_amount_minor
        CHECK (amount_minor IS NULL OR amount_minor > 0),

    CONSTRAINT ck_blockchain_version
        CHECK (version >= 0),

    CONSTRAINT ck_blockchain_confirmed_at
        CHECK (
            transaction_status <> 'CONFIRMED'
            OR confirmed_at IS NOT NULL
        )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '블록체인 자산 전송 거래';

CREATE INDEX idx_blockchain_status_updated
    ON blockchain_transaction (transaction_status, updated_at);

CREATE INDEX idx_blockchain_payment
    ON blockchain_transaction (payment_seq);

CREATE INDEX idx_blockchain_network_block
    ON blockchain_transaction (network_code, block_number);


-- ============================================================
-- 6. exchange_order
-- ============================================================

CREATE TABLE exchange_order (
    exchange_order_seq BIGINT NOT NULL AUTO_INCREMENT,
    exchange_order_id VARCHAR(50) NOT NULL,
    payment_seq BIGINT NOT NULL,
    exchange_provider_code VARCHAR(50) NOT NULL,
    client_order_id VARCHAR(100) NOT NULL,
    provider_order_id VARCHAR(100) NULL,
    order_side VARCHAR(10) NOT NULL,
    base_asset_code VARCHAR(20) NOT NULL,
    quote_currency VARCHAR(10) NOT NULL,
    requested_amount_minor BIGINT NOT NULL,
    executed_amount_minor BIGINT NULL,
    average_execution_rate DECIMAL(24, 12) NULL,
    received_amount BIGINT NULL,
    exchange_fee_amount BIGINT NULL,
    exchange_order_status VARCHAR(30) NOT NULL,
    failure_code VARCHAR(50) NULL,
    failure_message VARCHAR(500) NULL,
    requested_at DATETIME(6) NOT NULL,
    submitted_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT pk_exchange_order
        PRIMARY KEY (exchange_order_seq),

    CONSTRAINT uk_exchange_order_id
        UNIQUE (exchange_order_id),

    CONSTRAINT uk_exchange_client_order_id
        UNIQUE (client_order_id),

    CONSTRAINT uk_exchange_payment
        UNIQUE (payment_seq),

    CONSTRAINT fk_exchange_order_payment
        FOREIGN KEY (payment_seq)
        REFERENCES payment (payment_seq)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT ck_exchange_order_side
        CHECK (
            order_side IN (
                'BUY',
                'SELL'
            )
        ),

    CONSTRAINT ck_exchange_order_status
        CHECK (
            exchange_order_status IN (
                'REQUESTED',
                'SUBMITTED',
                'PROCESSING',
                'COMPLETED',
                'FAILED',
                'CANCELLED'
            )
        ),

    CONSTRAINT ck_exchange_requested_amount
        CHECK (requested_amount_minor > 0),

    CONSTRAINT ck_exchange_executed_amount
        CHECK (
            executed_amount_minor IS NULL
            OR executed_amount_minor >= 0
        ),

    CONSTRAINT ck_exchange_average_rate
        CHECK (
            average_execution_rate IS NULL
            OR average_execution_rate > 0
        ),

    CONSTRAINT ck_exchange_received_amount
        CHECK (
            received_amount IS NULL
            OR received_amount >= 0
        ),

    CONSTRAINT ck_exchange_fee_amount
        CHECK (
            exchange_fee_amount IS NULL
            OR exchange_fee_amount >= 0
        ),

    CONSTRAINT ck_exchange_version
        CHECK (version >= 0),

    CONSTRAINT ck_exchange_completed_values
        CHECK (
            exchange_order_status <> 'COMPLETED'
            OR (
                executed_amount_minor IS NOT NULL
                AND average_execution_rate IS NOT NULL
                AND received_amount IS NOT NULL
                AND completed_at IS NOT NULL
            )
        )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '거래소 USDC 매도 주문';

CREATE INDEX idx_exchange_status_updated
    ON exchange_order (exchange_order_status, updated_at);

CREATE INDEX idx_exchange_provider_order
    ON exchange_order (exchange_provider_code, provider_order_id);


-- ============================================================
-- 7. settlement_receivable
-- ============================================================

CREATE TABLE settlement_receivable (
    settlement_receivable_seq BIGINT NOT NULL AUTO_INCREMENT,
    settlement_receivable_id VARCHAR(50) NOT NULL,
    payment_seq BIGINT NOT NULL,
    merchant_seq BIGINT NOT NULL,
    exchange_order_seq BIGINT NULL,
    settlement_currency VARCHAR(10) NOT NULL,
    gross_amount BIGINT NOT NULL,
    fee_rate DECIMAL(12, 8) NOT NULL DEFAULT 0,
    fee_amount BIGINT NOT NULL DEFAULT 0,
    adjustment_amount BIGINT NOT NULL DEFAULT 0,
    net_amount BIGINT NOT NULL,
    exchange_received_amount BIGINT NULL,
    exchange_profit_loss_amount BIGINT NULL,
    receivable_status VARCHAR(30) NOT NULL,
    eligible_date DATE NOT NULL,
    assigned_settlement_seq BIGINT NULL,
    hold_reason_code VARCHAR(50) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT pk_settlement_receivable
        PRIMARY KEY (settlement_receivable_seq),

    CONSTRAINT uk_settlement_receivable_id
        UNIQUE (settlement_receivable_id),

    CONSTRAINT uk_settlement_receivable_payment
        UNIQUE (payment_seq),

    CONSTRAINT fk_settlement_receivable_payment
        FOREIGN KEY (payment_seq)
        REFERENCES payment (payment_seq)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT fk_settlement_receivable_merchant
        FOREIGN KEY (merchant_seq)
        REFERENCES merchant (merchant_seq)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT fk_settlement_receivable_exchange_order
        FOREIGN KEY (exchange_order_seq)
        REFERENCES exchange_order (exchange_order_seq)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT ck_settlement_receivable_gross_amount
        CHECK (gross_amount > 0),

    CONSTRAINT ck_settlement_receivable_fee_rate
        CHECK (fee_rate >= 0),

    CONSTRAINT ck_settlement_receivable_fee_amount
        CHECK (fee_amount >= 0),

    CONSTRAINT ck_settlement_receivable_net_amount
        CHECK (net_amount >= 0),

    CONSTRAINT ck_settlement_receivable_exchange_received
        CHECK (
            exchange_received_amount IS NULL
            OR exchange_received_amount >= 0
        ),

    CONSTRAINT ck_settlement_receivable_version
        CHECK (version >= 0),

    CONSTRAINT ck_settlement_receivable_status
        CHECK (
            receivable_status IN (
                'PENDING',
                'READY',
                'ASSIGNED',
                'SETTLED',
                'HELD',
                'CANCELLED'
            )
        ),

    CONSTRAINT ck_settlement_receivable_amount_formula
        CHECK (
            net_amount =
                gross_amount
                - fee_amount
                + adjustment_amount
        )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '결제 단위 원화 정산 대상';

CREATE INDEX idx_receivable_batch
    ON settlement_receivable (
        receivable_status,
        eligible_date,
        merchant_seq
    );

CREATE INDEX idx_receivable_merchant_created
    ON settlement_receivable (
        merchant_seq,
        created_at
    );


-- ============================================================
-- 8. webhook_delivery
-- ============================================================

CREATE TABLE webhook_delivery (
    webhook_delivery_seq BIGINT NOT NULL AUTO_INCREMENT,
    webhook_delivery_id VARCHAR(50) NOT NULL,
    merchant_seq BIGINT NOT NULL,
    event_id VARCHAR(50) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    aggregate_type VARCHAR(30) NOT NULL,
    aggregate_id VARCHAR(50) NOT NULL,
    destination_url VARCHAR(1000) NOT NULL,
    payload JSON NOT NULL,
    delivery_status VARCHAR(30) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    last_http_status INT NULL,
    last_error_message VARCHAR(1000) NULL,
    next_retry_at DATETIME(6) NULL,
    delivered_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT pk_webhook_delivery
        PRIMARY KEY (webhook_delivery_seq),

    CONSTRAINT uk_webhook_delivery_id
        UNIQUE (webhook_delivery_id),

    CONSTRAINT uk_webhook_event_merchant
        UNIQUE (event_id, merchant_seq),

    CONSTRAINT fk_webhook_delivery_merchant
        FOREIGN KEY (merchant_seq)
        REFERENCES merchant (merchant_seq)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT ck_webhook_delivery_attempt_count
        CHECK (attempt_count >= 0),

    CONSTRAINT ck_webhook_delivery_http_status
        CHECK (
            last_http_status IS NULL
            OR last_http_status BETWEEN 100 AND 599
        ),

    CONSTRAINT ck_webhook_delivery_version
        CHECK (version >= 0),

    CONSTRAINT ck_webhook_delivery_status
        CHECK (
            delivery_status IN (
                'PENDING',
                'DELIVERING',
                'SUCCEEDED',
                'RETRY_WAITING',
                'FAILED'
            )
        ),

    CONSTRAINT ck_webhook_delivery_delivered_at
        CHECK (
            delivery_status <> 'SUCCEEDED'
            OR delivered_at IS NOT NULL
        )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '가맹점 Webhook 전송 이력';

CREATE INDEX idx_webhook_retry
    ON webhook_delivery (
        delivery_status,
        next_retry_at
    );

CREATE INDEX idx_webhook_merchant_created
    ON webhook_delivery (
        merchant_seq,
        created_at
    );


-- ============================================================
-- 9. outbox_event
-- ============================================================

CREATE TABLE outbox_event (
    outbox_event_seq BIGINT NOT NULL AUTO_INCREMENT,
    event_id VARCHAR(50) NOT NULL,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(50) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSON NOT NULL,
    event_status VARCHAR(30) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_at DATETIME(6) NULL,
    occurred_at DATETIME(6) NOT NULL,
    published_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,

    CONSTRAINT pk_outbox_event
        PRIMARY KEY (outbox_event_seq),

    CONSTRAINT uk_outbox_event_id
        UNIQUE (event_id),

    CONSTRAINT ck_outbox_retry_count
        CHECK (retry_count >= 0),

    CONSTRAINT ck_outbox_event_status
        CHECK (
            event_status IN (
                'PENDING',
                'PROCESSING',
                'PUBLISHED',
                'RETRY_WAITING',
                'FAILED'
            )
        ),

    CONSTRAINT ck_outbox_published_at
        CHECK (
            event_status <> 'PUBLISHED'
            OR published_at IS NOT NULL
        )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Transactional Outbox 이벤트';

CREATE INDEX idx_outbox_publish
    ON outbox_event (
        event_status,
        next_retry_at,
        created_at
    );


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

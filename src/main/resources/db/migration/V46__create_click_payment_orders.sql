-- Durable Click payment intent. Redis remains a cache only; a paid Click order
-- must retain its driver and amount across Redis TTL expiry or a process restart.
CREATE TABLE click_payment_orders (
    merchant_trans_id VARCHAR(64) PRIMARY KEY,
    driver_id         BIGINT       NOT NULL REFERENCES drivers(id),
    amount            BIGINT       NOT NULL CHECK (amount > 0),
    status            VARCHAR(20)  NOT NULL DEFAULT 'CREATED',
    click_trans_id    VARCHAR(64),
    click_paydoc_id   VARCHAR(64),
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_click_payment_order_status
        CHECK (status IN ('CREATED', 'PREPARED', 'CONFIRMED', 'CANCELLED'))
);

CREATE UNIQUE INDEX uq_click_payment_order_click_trans
    ON click_payment_orders(click_trans_id) WHERE click_trans_id IS NOT NULL;
CREATE UNIQUE INDEX uq_click_payment_order_click_paydoc
    ON click_payment_orders(click_paydoc_id) WHERE click_paydoc_id IS NOT NULL;
CREATE INDEX idx_click_payment_orders_driver_status
    ON click_payment_orders(driver_id, status);

-- Shop API includes click_paydoc_id. Preserve it in the durable callback ledger
-- for reconciliation without changing any historic transaction identifier.
ALTER TABLE click_transactions
    ADD COLUMN IF NOT EXISTS click_paydoc_id VARCHAR(64);
CREATE UNIQUE INDEX IF NOT EXISTS uq_click_transactions_click_paydoc
    ON click_transactions(click_paydoc_id) WHERE click_paydoc_id IS NOT NULL;

-- Preserve the three historic callback records as durable payment orders. This
-- backfill is read-only with respect to their financial state and is idempotent.
INSERT INTO click_payment_orders (
    merchant_trans_id, driver_id, amount, status, click_trans_id,
    created_at, updated_at
)
SELECT DISTINCT ON (merchant_trans_id)
    merchant_trans_id,
    driver_id,
    amount,
    status,
    click_trans_id,
    created_at,
    COALESCE(completed_at, created_at)
FROM click_transactions
ORDER BY merchant_trans_id, created_at DESC
ON CONFLICT (merchant_trans_id) DO NOTHING;

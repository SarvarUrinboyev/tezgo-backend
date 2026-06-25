-- Click ADVANCED SHOP idempotency ledger.
--
-- Separate from the working Merchant API's `click_transactions` table (V41).
-- Keyed on click_paydoc_id (the ADVANCED SHOP natural identifier — Click's payment-doc id,
-- analogous to click_trans_id in the Merchant API). UNIQUE(click_paydoc_id) +
-- conditional UPDATE on status (PREPARED -> CONFIRMED) is the same double-credit guard
-- the Merchant API uses, just on its own table so the two integrations never collide.
--
-- Money flow:
--   PREPARE  (action=1) -> insertIfAbsent: row exists with status='PREPARED'
--   COMPLETE (action=2) -> insertIfAbsent (defensive) + markConfirmedIfNotAlready:
--                          atomic row claim; only the row-update winner credits balance.
--   Bad sign / cancel  -> markCancelled (if not already CONFIRMED).

CREATE TABLE click_shop_transactions (
    id                   BIGSERIAL PRIMARY KEY,
    click_paydoc_id      VARCHAR(64)  NOT NULL,
    attempt_trans_id     VARCHAR(64),
    driver_id            BIGINT REFERENCES drivers(id),
    -- amount in tiyin (drivers.balance + transactions.amount are tiyin too)
    amount               BIGINT       NOT NULL,
    -- action: 1=PREPARE, 2=COMPLETE (ADVANCED SHOP codes — distinct from Merchant API 0/1)
    action               INTEGER      NOT NULL DEFAULT 1,
    -- status: PREPARED | CONFIRMED | CANCELLED
    status               VARCHAR(20)  NOT NULL DEFAULT 'PREPARED',
    merchant_prepare_id  VARCHAR(64),
    merchant_confirm_id  VARCHAR(64),
    error                INTEGER      NOT NULL DEFAULT 0,
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at         TIMESTAMP,
    CONSTRAINT uq_click_shop_paydoc_id UNIQUE (click_paydoc_id),
    CONSTRAINT chk_click_shop_status
        CHECK (status IN ('PREPARED', 'CONFIRMED', 'CANCELLED'))
);

CREATE INDEX idx_click_shop_attempt_trans
    ON click_shop_transactions (attempt_trans_id);

CREATE INDEX idx_click_shop_driver
    ON click_shop_transactions (driver_id);

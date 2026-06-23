-- V41: Durable idempotency ledger for Click payment callbacks.
--
-- WHY: Click can re-deliver the same COMPLETE callback (network retry, our timeout,
-- their reconciliation job). Before V41 the only de-dup was a 30s Redis SETNX + a 24h
-- Redis order-status check — both vanish on a Redis outage/TTL, so a redelivered COMPLETE
-- could double-credit a driver's balance. This table is the DURABLE, Redis-independent guard:
--   * UNIQUE(click_trans_id) makes the row the single source of truth per Click transaction.
--   * The COMPLETE handler flips status PREPARED->CONFIRMED with a conditional UPDATE
--     (WHERE status <> 'CONFIRMED'); only the first caller gets rowcount=1 and credits.
-- The actual money/ledger write still goes through transactions + creditDriverBalance
-- (same path as Payme and admin top-ups) so balances reconcile.

CREATE TABLE IF NOT EXISTS click_transactions (
    id                  BIGSERIAL PRIMARY KEY,
    click_trans_id      VARCHAR(64)  NOT NULL,
    merchant_trans_id   VARCHAR(64)  NOT NULL,
    driver_id           BIGINT       REFERENCES drivers(id),
    amount              BIGINT       NOT NULL,                  -- TIYIN (so'm * 100); matches drivers.balance + transactions.amount
    action              INTEGER      NOT NULL DEFAULT 0,        -- 0 = PREPARE, 1 = COMPLETE
    status              VARCHAR(20)  NOT NULL DEFAULT 'PREPARED', -- PREPARED | CONFIRMED | CANCELLED
    merchant_prepare_id VARCHAR(64),
    merchant_confirm_id VARCHAR(64),
    error               INTEGER      NOT NULL DEFAULT 0,        -- last Click error code echoed
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at        TIMESTAMP,
    CONSTRAINT uq_click_trans_id UNIQUE (click_trans_id),
    CONSTRAINT chk_click_tx_status CHECK (status IN ('PREPARED','CONFIRMED','CANCELLED'))
);

CREATE INDEX IF NOT EXISTS idx_click_tx_merchant_trans ON click_transactions(merchant_trans_id);

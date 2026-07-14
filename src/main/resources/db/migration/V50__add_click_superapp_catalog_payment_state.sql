-- CLICK SuperApp catalog payments use merchant_trans_id = reusable TZ-XXXX account.
-- They must therefore live in the canonical Click transaction ledger, but cannot
-- be inferred as app-created one-time click_payment_orders.
--
-- V49 is reserved by the separate, undeployed AO-P1-03 worktree.  V50 is the
-- first free version after inspecting both canonical worktrees on 2026-07-14.

ALTER TABLE click_transactions
    ADD COLUMN payment_source VARCHAR(32) NOT NULL DEFAULT 'APP_LINK';

ALTER TABLE click_transactions
    ADD COLUMN catalog_account VARCHAR(20);

ALTER TABLE click_transactions
    ADD CONSTRAINT chk_click_transaction_source
        CHECK (payment_source IN ('APP_LINK', 'CLICK_SUPERAPP'));

ALTER TABLE click_transactions
    ADD CONSTRAINT chk_click_transaction_catalog_account
        CHECK ((payment_source = 'APP_LINK' AND catalog_account IS NULL)
            OR (payment_source = 'CLICK_SUPERAPP' AND catalog_account IS NOT NULL));

-- A catalog account is reusable; the generated prepare identifier is the local
-- payment-intent identifier and must be unique only inside the catalog flow.
CREATE UNIQUE INDEX uq_click_transactions_catalog_prepare_id
    ON click_transactions(merchant_prepare_id)
    WHERE payment_source = 'CLICK_SUPERAPP' AND merchant_prepare_id IS NOT NULL;

CREATE INDEX idx_click_transactions_catalog_account_status
    ON click_transactions(catalog_account, status)
    WHERE payment_source = 'CLICK_SUPERAPP';

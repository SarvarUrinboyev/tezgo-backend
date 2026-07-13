-- TEZGO Click reconciliation report (READ ONLY).
-- Run with: sudo -u postgres psql tezyol -v ON_ERROR_STOP=1 -f scripts/click-reconcile.sql
-- This report never changes balances, payment state, Redis, or wallet records.

BEGIN TRANSACTION READ ONLY;

-- 1. A confirmed Click callback with no matching TOPUP ledger credit.
SELECT 'confirmed_without_ledger_credit' AS anomaly,
       c.click_trans_id, c.merchant_trans_id, c.driver_id, c.amount,
       c.completed_at
FROM click_transactions c
LEFT JOIN transactions t
       ON t.driver_id = c.driver_id
      AND t.type = 'TOPUP'
      AND t.amount = c.amount
      AND t.description = ('Click to''lovi #' || c.merchant_trans_id)
WHERE c.status = 'CONFIRMED'
  AND t.id IS NULL
ORDER BY c.completed_at;

-- 2. A Click-labelled wallet credit without a confirmed Click callback.
SELECT 'ledger_credit_without_confirmed_click' AS anomaly,
       t.id AS ledger_id, t.driver_id, t.amount, t.description, t.created_at
FROM transactions t
LEFT JOIN click_transactions c
       ON c.driver_id = t.driver_id
      AND c.amount = t.amount
      AND c.status = 'CONFIRMED'
      AND t.description = ('Click to''lovi #' || c.merchant_trans_id)
WHERE t.type = 'TOPUP'
  AND t.description LIKE 'Click to''lovi #%'
  AND c.id IS NULL
ORDER BY t.created_at;

-- 3. Duplicate external identifiers (both should return zero rows).
SELECT 'duplicate_click_trans_id' AS anomaly, click_trans_id, count(*) AS row_count
FROM click_transactions
GROUP BY click_trans_id
HAVING count(*) > 1;

SELECT 'duplicate_merchant_trans_id' AS anomaly, merchant_trans_id, count(*) AS row_count
FROM click_transactions
GROUP BY merchant_trans_id
HAVING count(*) > 1;

-- 4. Confirmed callback whose durable order is absent, points to another driver,
--    or has a different internal tiyin amount.
SELECT 'confirmed_order_mapping_mismatch' AS anomaly,
       c.click_trans_id, c.merchant_trans_id, c.driver_id AS click_driver_id,
       c.amount AS click_amount, o.driver_id AS order_driver_id,
       o.amount AS order_amount, o.status AS order_status
FROM click_transactions c
LEFT JOIN click_payment_orders o ON o.merchant_trans_id = c.merchant_trans_id
WHERE c.status = 'CONFIRMED'
  AND (o.merchant_trans_id IS NULL
       OR o.driver_id IS DISTINCT FROM c.driver_id
       OR o.amount IS DISTINCT FROM c.amount
       OR o.status <> 'CONFIRMED')
ORDER BY c.completed_at;

-- 5. Prepared callbacks that have remained unfinished for more than 30 minutes.
SELECT 'stale_prepared_callback' AS anomaly,
       click_trans_id, merchant_trans_id, driver_id, amount, created_at
FROM click_transactions
WHERE status = 'PREPARED'
  AND created_at < now() - interval '30 minutes'
ORDER BY created_at;

-- 6. Failed/cancelled payment that nevertheless has a Click-labelled credit.
SELECT 'cancelled_payment_with_ledger_credit' AS anomaly,
       c.click_trans_id, c.merchant_trans_id, c.driver_id, c.amount, c.error,
       t.id AS ledger_id, t.created_at AS ledger_created_at
FROM click_transactions c
JOIN transactions t
  ON t.driver_id = c.driver_id
 AND t.type = 'TOPUP'
 AND t.amount = c.amount
 AND t.description = ('Click to''lovi #' || c.merchant_trans_id)
WHERE c.status = 'CANCELLED'
ORDER BY c.completed_at;

-- 7. Durable payment intent that is still pending. For each result, compare the
--    Redis key manually, for example:
--    redis-cli EXISTS payment:order:<merchant_trans_id>
SELECT 'pending_durable_order' AS review_item,
       merchant_trans_id, driver_id, amount, status, created_at, updated_at
FROM click_payment_orders
WHERE status IN ('CREATED', 'PREPARED')
ORDER BY created_at;

COMMIT;

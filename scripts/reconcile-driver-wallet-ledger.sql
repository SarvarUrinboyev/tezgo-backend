-- TEZGO driver wallet reconciliation (READ ONLY).
-- Run with: sudo -u postgres psql -v ON_ERROR_STOP=1 -d tezyol -f reconcile-driver-wallet-ledger.sql
-- This script contains no INSERT, UPDATE, DELETE, DDL, or sequence mutation.

BEGIN TRANSACTION READ ONLY;

-- 1. Ledger-chain breaks. A valid row delta alone is insufficient: compare every
--    snapshot to its chronological neighbours (created_at, id) for the same driver.
WITH ordered AS (
    SELECT
        t.id,
        t.driver_id,
        t.created_at,
        t.type,
        t.amount,
        t.balance_before,
        t.balance_after,
        LAG(t.balance_after) OVER ledger_window AS previous_balance_after,
        LEAD(t.balance_before) OVER ledger_window AS next_balance_before,
        t.balance_after - t.balance_before AS recorded_delta,
        CASE
            WHEN t.type = 'COMMISSION' THEN -t.amount
            ELSE t.amount
        END AS expected_delta
    FROM transactions t
    WINDOW ledger_window AS (PARTITION BY t.driver_id ORDER BY t.created_at, t.id)
)
SELECT
    id, driver_id, created_at, type, amount,
    balance_before, balance_after,
    previous_balance_after, next_balance_before,
    recorded_delta, expected_delta,
    (previous_balance_after IS NOT NULL AND previous_balance_after <> balance_before) AS breaks_previous_chain,
    (next_balance_before IS NOT NULL AND next_balance_before <> balance_after) AS breaks_next_chain,
    (recorded_delta <> expected_delta) AS invalid_row_delta
FROM ordered
WHERE (previous_balance_after IS NOT NULL AND previous_balance_after <> balance_before)
   OR (next_balance_before IS NOT NULL AND next_balance_before <> balance_after)
   OR recorded_delta <> expected_delta
ORDER BY driver_id, created_at, id;

-- 2. Each driver's latest ledger snapshot must agree with drivers.balance.
WITH ranked AS (
    SELECT
        t.driver_id,
        t.id AS ledger_id,
        t.created_at,
        t.balance_after,
        ROW_NUMBER() OVER (PARTITION BY t.driver_id ORDER BY t.created_at DESC, t.id DESC) AS row_number
    FROM transactions t
)
SELECT
    d.id AS driver_id,
    d.driver_code,
    d.balance AS current_driver_balance,
    r.ledger_id,
    r.created_at AS latest_ledger_at,
    r.balance_after AS latest_ledger_balance_after
FROM drivers d
JOIN ranked r ON r.driver_id = d.id AND r.row_number = 1
WHERE d.balance <> r.balance_after
ORDER BY d.id;

-- 3. Every confirmed canonical Click order needs exactly one confirmed Click row
--    and exactly one matching TOPUP ledger record; amounts must agree.
WITH click_matches AS (
    SELECT
        o.merchant_trans_id,
        o.driver_id,
        o.amount AS order_amount,
        (
            SELECT COUNT(*)
            FROM click_transactions c
            WHERE c.merchant_trans_id = o.merchant_trans_id
              AND c.status = 'CONFIRMED'
        ) AS confirmed_click_rows,
        (
            SELECT COUNT(*)
            FROM transactions t
            WHERE t.driver_id = o.driver_id
              AND t.type = 'TOPUP'
              AND t.amount = o.amount
              AND t.description = ('Click to''lovi #' || o.merchant_trans_id)
        ) AS matching_topup_rows,
        (
            SELECT MIN(t.amount)
            FROM transactions t
            WHERE t.driver_id = o.driver_id
              AND t.type = 'TOPUP'
              AND t.description = ('Click to''lovi #' || o.merchant_trans_id)
        ) AS ledger_amount
    FROM click_payment_orders o
    WHERE o.status = 'CONFIRMED'
)
SELECT *
FROM click_matches
WHERE confirmed_click_rows <> 1
   OR matching_topup_rows <> 1
   OR ledger_amount IS DISTINCT FROM order_amount
ORDER BY merchant_trans_id;

-- 4. No non-confirmed canonical Click order may have a matching Click TOPUP ledger row.
SELECT
    o.merchant_trans_id,
    o.driver_id,
    o.status AS order_status,
    t.id AS ledger_id,
    t.amount,
    t.created_at
FROM click_payment_orders o
JOIN transactions t
  ON t.driver_id = o.driver_id
 AND t.type = 'TOPUP'
 AND t.description = ('Click to''lovi #' || o.merchant_trans_id)
WHERE o.status <> 'CONFIRMED'
ORDER BY o.merchant_trans_id, t.id;

-- 5. Explicit duplicate identifiers in the durable Click tables.
SELECT merchant_trans_id, COUNT(*) AS confirmed_click_rows
FROM click_transactions
WHERE status = 'CONFIRMED'
GROUP BY merchant_trans_id
HAVING COUNT(*) > 1
ORDER BY merchant_trans_id;

SELECT click_trans_id, COUNT(*) AS row_count
FROM click_transactions
GROUP BY click_trans_id
HAVING COUNT(*) > 1
ORDER BY click_trans_id;

-- 6. Potential stale-snapshot rows: the arithmetic can still be internally valid,
--    so flag a TOPUP whose predecessor or successor does not join its chain.
WITH ordered_topups AS (
    SELECT
        t.id,
        t.driver_id,
        t.created_at,
        t.amount,
        t.balance_before,
        t.balance_after,
        t.description,
        LAG(t.balance_after) OVER (PARTITION BY t.driver_id ORDER BY t.created_at, t.id) AS previous_balance_after,
        LEAD(t.balance_before) OVER (PARTITION BY t.driver_id ORDER BY t.created_at, t.id) AS next_balance_before
    FROM transactions t
    WHERE t.type = 'TOPUP'
)
SELECT
    id, driver_id, created_at, amount, balance_before, balance_after, description,
    previous_balance_after, next_balance_before
FROM ordered_topups
WHERE (previous_balance_after IS NOT NULL AND previous_balance_after <> balance_before)
   OR (next_balance_before IS NOT NULL AND next_balance_before <> balance_after)
ORDER BY driver_id, created_at, id;

COMMIT;

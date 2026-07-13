# Click ledger P1 incident — 2026-07-13

> Historical correction is deliberately **not executed**. Execute nothing in
> this document unless the owner explicitly says `EXECUTE LEDGER CORRECTION`.

## Confirmed payment

| Field | Value |
|---|---|
| `merchant_trans_id` | `502a799a77ef4e67a350` |
| `click_trans_id` | `3781764694` |
| `click_paydoc_id` | `5150237243` |
| Driver | `id=5`, `TZ-0005` |
| Amount | `100,000` tiyin (`1,000 UZS`) |
| Complete time | `2026-07-13 13:52:38 UTC` |

The payment was confirmed once, credited the real driver balance once, and
created one TOPUP row. The defect is only the row's stale balance snapshots.

## Target metadata row

| Field | Captured value |
|---|---:|
| `transactions.id` | `118` |
| `driver_id` | `5` |
| `type` | `TOPUP` |
| `amount` | `100000` |
| `created_at` | `2026-07-13 13:52:38.236602 UTC` |
| description | `Click to'lovi #502a799a77ef4e67a350` |
| recorded `balance_before` | `9227037` |
| recorded `balance_after` | `9327037` |
| authoritative `balance_before` | `9327037` |
| authoritative `balance_after` | `9427037` |
| current `drivers.balance` captured during audit | `9427037` |

Adjacent chronological ledger evidence:

| Row | Time (UTC) | `balance_before` | `balance_after` |
|---|---|---:|---:|
| `116` | `2026-07-09 16:01:34.609657` | `9327037` | `9327037` |
| `118` | `2026-07-13 13:52:38.236602` | `9227037` | `9327037` |

There is no later driver-5 ledger row at capture time. Correcting row 118's
metadata would connect it to row 116 and to the current driver balance, but
would not repair older independent chain breaks.

## Root cause and code fix

The old code used a JPQL bulk `UPDATE drivers SET balance = balance + ?` and
then read the same managed `Driver`. Hibernate returned the stale persistence-
context entity, so the snapshots were one mutation behind.

The fix removes every production `addToBalance` call. Each driver wallet path
loads the driver using `PESSIMISTIC_WRITE`, captures `balance_before`, applies
integer exact arithmetic to the managed entity, and persists the ledger row in
the caller's transaction. It covers canonical Click, Advanced Shop, Payme/
driver top-up, admin top-up, trip commission, and taxometer commission.

## Read-only reconciliation result

`scripts/reconcile-driver-wallet-ledger.sql` was run under `BEGIN READ ONLY`.

- The canonical Click reconciliation reported no confirmed-order mismatch, no
  non-confirmed order credited, and no duplicate canonical Click identifier.
- The chain query flagged 107 rows. This is the number of rows adjacent to a
  discontinuity, not a count of independent defects.
- Seven drivers had a latest-ledger snapshot different from `drivers.balance`:
  `TZ-0005`, `TZ-0008`, `TZ-0012`, `TZ-0013`, `TZ-0018`, `TZ-0019`, `TZ-0021`.
- The TOPUP-specific stale-snapshot candidate query returned eight rows:
  driver 5 rows `2, 54, 81, 98, 106, 118`; driver 6 rows `6, 66`.

These results require a separately approved historical reconciliation project.
No production ledger, payment, Click, or driver balance record was changed.

## Proposed correction for row 118 only — do not run yet

### 1. Capture a before-state backup outside the database

Run only after approval, save the output as an incident artifact, and retain it
with the deployment receipts:

```powershell
ssh -i C:\Users\Laptop\.ssh\tezyol_vps root@137.184.17.18 `
  "sudo -u postgres psql -X -d tezyol -c \"COPY (
      SELECT t.*, d.balance AS current_driver_balance
      FROM transactions t JOIN drivers d ON d.id=t.driver_id
      WHERE t.id=118 AND t.driver_id=5
  ) TO STDOUT WITH CSV HEADER\"" > click-ledger-row-118-before.csv
```

### 2. Conditional correction transaction

This changes only the two snapshot fields. Every identity, amount, description,
payment state, Click identifier, and `drivers.balance` value is constrained in
the predicate and remains unchanged. Any row count other than one aborts.

```sql
BEGIN;

DO $$
DECLARE changed_count integer;
BEGIN
    UPDATE transactions
       SET balance_before = 9327037,
           balance_after  = 9427037
     WHERE id = 118
       AND driver_id = 5
       AND type = 'TOPUP'
       AND amount = 100000
       AND description = 'Click to''lovi #502a799a77ef4e67a350'
       AND balance_before = 9227037
       AND balance_after = 9327037;

    GET DIAGNOSTICS changed_count = ROW_COUNT;
    IF changed_count <> 1 THEN
        RAISE EXCEPTION 'row 118 correction aborted: expected 1 row, changed %', changed_count;
    END IF;
END $$;

SELECT id, driver_id, type, amount, description, balance_before, balance_after
FROM transactions
WHERE id = 118;

COMMIT;
```

### 3. Required verification

```sql
SELECT t.id, t.balance_before, t.balance_after, d.balance AS current_driver_balance
FROM transactions t
JOIN drivers d ON d.id = t.driver_id
WHERE t.id = 118;
```

Expected values are `9327037`, `9427037`, and `9427037` respectively.

### 4. Conditional rollback

If and only if the approved correction must be reverted, use the inverse
conditional statement and require exactly one affected row:

```sql
BEGIN;

DO $$
DECLARE changed_count integer;
BEGIN
    UPDATE transactions
       SET balance_before = 9227037,
           balance_after  = 9327037
     WHERE id = 118
       AND driver_id = 5
       AND type = 'TOPUP'
       AND amount = 100000
       AND description = 'Click to''lovi #502a799a77ef4e67a350'
       AND balance_before = 9327037
       AND balance_after = 9427037;

    GET DIAGNOSTICS changed_count = ROW_COUNT;
    IF changed_count <> 1 THEN
        RAISE EXCEPTION 'row 118 rollback aborted: expected 1 row, changed %', changed_count;
    END IF;
END $$;

COMMIT;
```

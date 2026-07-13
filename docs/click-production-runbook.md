# Click production runbook

This runbook covers the approved Shop API callbacks only:

- `POST /api/payment/click/prepare`
- `POST /api/payment/click/complete`

They belong to Click service ID `105926`. The Advanced Shop endpoints are a
separate integration and must not be used for this driver top-up flow.

## Safety

Never print or paste `CLICK_SECRET_KEY`, `CLICK_ADVANCED_SHOP_SECRET_KEY`,
`JWT_SECRET`, database passwords, Redis passwords, or the full environment
file. Do not manually replay a real Complete callback. Do not correct a balance
from this runbook; preserve evidence and escalate a reconciliation anomaly.

## Routine verification

On the production server, use the commands below. They are read-only except
for the documented deploy/rollback actions.

```bash
systemctl status tezyol --no-pager
ss -lntp | grep -E ':443|:18080'
journalctl -u tezyol --since '30 minutes ago' --no-pager \
  | grep -E '\[CLICK\]|payment/click'
sudo -u postgres psql tezyol -v ON_ERROR_STOP=1 \
  -f /opt/tezyol/scripts/click-reconcile.sql
```

To investigate one known order without exposing secrets:

```sql
SELECT merchant_trans_id, driver_id, amount, status, click_trans_id,
       click_paydoc_id, created_at, updated_at
FROM click_payment_orders
WHERE merchant_trans_id = '<order-id>';

SELECT click_trans_id, click_paydoc_id, merchant_trans_id, driver_id, amount,
       status, merchant_prepare_id, merchant_confirm_id, error, created_at,
       completed_at
FROM click_transactions
WHERE merchant_trans_id = '<order-id>';

SELECT id, driver_id, type, amount, balance_before, balance_after, description,
       created_at
FROM transactions
WHERE description = ('Click to''lovi #' || '<order-id>');
```

Redis is a cache, not the payment source of truth. It is safe to inspect a
specific key after the durable SQL lookup:

```bash
redis-cli EXISTS payment:order:<order-id>
```

## Callback diagnosis

- No nginx access entry: Click-side routing/configuration is the likely issue.
- `301`/`302`: approved callback path or proxy redirect mismatch.
- `403`: Spring Security or CSRF routing regression.
- `404`: stale artifact or controller mapping mismatch.
- `500`: retain the stack trace and roll back if caused by the deployment.
- `[CLICK][SIGNATURE_INVALID]`: verify service ID, secret pairing and Click
  signature contract without printing the secret.
- `[CLICK][DUPLICATE_NOOP]`: retry was safely not credited twice.
- `[CLICK][FAILED]`: no balance credit is expected.

## Controlled 1,000 UZS proof

Before asking an owner to pay, capture driver ID/code, balance in tiyin, the
current Tashkent and UTC times, and start monitoring:

```bash
tail -F /var/log/nginx/access.log | grep --line-buffered -E \
  '/api/payment/click/(prepare|complete)|/api/payment/click-shop'
journalctl -u tezyol -f --no-pager -o short-iso
```

For a 1,000 UZS payment, expected credit is exactly 100,000 tiyin. Verify one
Prepare, one successful Complete, one `click_transactions` row, one matching
`transactions` TOPUP row, and a balance delta of exactly 100,000 tiyin.

## Deployment and rollback

Build only from `C:\Users\Laptop\Documents\tezgo-backend-source`. Before a
restart, create a labelled JAR and PostgreSQL backup, record local/uploaded/
installed SHA-256 values, then verify Flyway and the Click invalid-signature
probe after the service starts.

Rollback pattern (substitute the actual labelled backup filename):

```bash
cp /opt/tezyol/tezyol.jar.bak-click-final-<timestamp> /opt/tezyol/tezyol.jar
systemctl restart tezyol
systemctl status tezyol --no-pager
```

## Current evidence status

At the time this runbook was added, no new real 1,000 UZS production Click
payment had been performed. Approval of callback URLs is not payment proof.

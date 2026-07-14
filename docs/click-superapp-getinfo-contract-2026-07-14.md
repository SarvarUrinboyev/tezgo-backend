# TEZGO Click SuperApp GetInfo contract — 2026-07-14

Status: local implementation package only. Do not configure this URL in the
Click Merchant cabinet until Click accepts these examples and the deployment
owner installs the approved authentication configuration. The default runtime
mode is `OFF`; this document is not deployment authorization.

## Endpoint

| Item | Value |
| --- | --- |
| URL | `https://137-184-17-18.nip.io/api/payment/click-shop/getinfo` |
| Method | `POST` |
| Content-Type | `application/json` |
| Service ID | `105926` |
| Request authentication | Optional Basic Auth capability. The production readiness gate stays closed as `GETINFO_AUTH_NOT_CONFIGURED` until Click confirms its exact rule and the owner sets `CLICK_GETINFO_AUTH_READY=true`; if Basic Auth is required, its credentials must also be installed. |
| Returned parameter | `params.full_name` only |

## Master catalog mode

`CLICK_SUPERAPP_CATALOG_MODE` is the single authoritative, fail-closed switch
for the whole catalog flow. Blank or unrecognized values are `OFF`.

| Mode | GetInfo | New catalog Prepare | Existing catalog Complete | App payment-link flow |
| --- | --- | --- | --- | --- |
| `OFF` (default) | disabled | disabled, no intent row | disabled, no credit | unchanged |
| `DRAIN` | disabled | disabled | only durable `PREPARED` settlement and `CONFIRMED` replay | unchanged |
| `ON` | allowed only when `CLICK_GETINFO_ENABLED=true`, `CLICK_GETINFO_AUTH_READY=true`, and all subordinate auth/service checks pass | allowed | normal typed catalog state machine | unchanged |

Operations must use `ON -> DRAIN -> reconcile/settle -> OFF`; an `OFF`
transition intentionally leaves historical `PREPARED` rows untouched rather
than silently crediting or deleting them. Their disposition requires an owner
approved reconciliation procedure.

GetInfo does not accept or require `sign_time`, `sign_string`, Click secret
keys, card data, or any payment amount. It is read-only: it creates no order,
Click transaction, ledger row, Redis value, driver update, or balance change.

## Successful example

The account is normalized by trimming whitespace and converting to uppercase.
Only the public driver-code form `TZ-` followed by at least four digits is
accepted. The name below is synthetic.

```bash
curl --request POST \
  --url 'https://137-184-17-18.nip.io/api/payment/click-shop/getinfo' \
  --header 'Content-Type: application/json' \
  --user '<configured-readonly-username>:<configured-readonly-password>' \
  --data '{"action":0,"service_id":105926,"params":{"account":"TZ-0005"}}'
```

```json
{
  "error": 0,
  "error_note": "Success",
  "params": {
    "full_name": "Example Driver"
  }
}
```

## Error examples

The error-note labels and codes below are TEZGO's proposed protocol responses
for Click review. They must be accepted by Click before the route is enabled.

| Case | Request fragment | Response example |
| --- | --- | --- |
| Unknown account | `"account":"TZ-9999"` | `{"error":-5,"error_note":"ACCOUNT_NOT_FOUND"}` |
| Malformed account | `"account":"9999"` | `{"error":-8,"error_note":"MALFORMED_ACCOUNT"}` |
| Blocked/inactive account | `"account":"TZ-0005"` | `{"error":-5,"error_note":"ACCOUNT_NOT_ELIGIBLE"}` |
| Wrong service ID | `"service_id":105927` | `{"error":-8,"error_note":"INVALID_SERVICE_ID"}` |
| Invalid action | `"action":1` | `{"error":-3,"error_note":"INVALID_ACTION"}` |
| Missing params/account | no `params.account` | `{"error":-8,"error_note":"MALFORMED_ACCOUNT"}` |
| Missing/invalid Basic Auth when enabled | absent or invalid Authorization header | `{"error":-1,"error_note":"UNAUTHORIZED"}` |
| Catalog mode or subordinate endpoint disabled | mode is not `ON`, or `CLICK_GETINFO_ENABLED=false` | `{"error":-1,"error_note":"GETINFO_NOT_ENABLED"}` |
| Auth readiness not configured | `CLICK_GETINFO_AUTH_READY=false`, or Basic Auth enabled with blank credentials | `{"error":-1,"error_note":"GETINFO_AUTH_NOT_CONFIGURED"}` |
| Low-volume rate limit | same direct trusted peer/account exceeds its configured window, or peer exceeds its higher source ceiling | `{"error":-8,"error_note":"RATE_LIMITED"}` |
| Malformed JSON | invalid JSON body | HTTP `400`, `{"error":-8,"error_note":"MALFORMED_REQUEST"}` |
| Wrong or missing media type | not JSON | HTTP `415`, `{"error":-8,"error_note":"MALFORMED_REQUEST"}` |
| Oversized body | body declared above 4 KiB | HTTP `413`, `{"error":-8,"error_note":"MALFORMED_REQUEST"}` |
| Temporary internal failure | safe internal lookup failure | `{"error":-7,"error_note":"TEMPORARY_ERROR"}` |

The service never logs an Authorization header, password, driver full name, or
the unmasked account value. Rate limiting uses a hash of the direct, trusted
peer plus normalized account for the narrow limit, and a separately configured
higher peer ceiling for broad enumeration control. Old buckets are evicted when
bounded in-memory capacity is reached; capacity cannot become a global ban or
make one Click egress IP reject unrelated driver accounts. It is not
authentication.

## Catalog payment handoff

After GetInfo returns `full_name`, Click sends the typed account value as
`merchant_trans_id` to the existing merchant callbacks:

```text
POST /api/payment/click/prepare
POST /api/payment/click/complete
```

Both use service ID `105926` and the existing signed Merchant API callback
contract. For a catalog payment, amount is decimal UZS and is converted exactly
to tiyin (`1,000` UZS becomes `100,000` tiyin). `merchant_trans_id=TZ-0005` is
reusable; idempotency is based on Click transaction identifiers and the unique
internally generated `merchant_prepare_id`, never on the driver code alone.

The old JSON routes `/api/payment/click-shop/prepare` and
`/api/payment/click-shop/complete` are intentionally denied. They are not part
of this integration and cannot credit a wallet.

## External acceptance and ingress validation plan

The protocol error labels and numeric codes above are proposed values until
Click confirms them. Before any cabinet change or deployment, obtain from Click
in writing: GetInfo authentication/signature rule, the exact GetInfo response
schema/error codes, and the catalog Prepare/Complete payload examples.

The production ingress validation must be read-only and use a synthetic or
owner-approved account only:

1. Verify `POST /api/payment/click-shop/getinfo` reaches the intended host over
   TLS and returns only the protocol JSON schema for malformed media/body.
2. Verify a valid authenticated GetInfo request reaches the service, returns
   only `params.full_name`, and makes no DB/Redis/ledger mutation.
3. Verify the canonical `/api/payment/click/prepare` and `/complete` URLs stay
   unchanged and that legacy `/api/payment/click-shop/{prepare,complete}` stay
   denied.
4. Keep catalog mode `OFF` and `CLICK_GETINFO_AUTH_READY=false` until Click's
   written contract and the owner-approved activation window are both present.
   No real payment is part of this plan.

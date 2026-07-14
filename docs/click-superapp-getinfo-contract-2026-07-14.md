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
| Request authentication | CLICK confirms that Basic Auth is supported. No credential is generated, stored in this document, or sent by this work; the production readiness gate remains closed with `CLICK_GETINFO_AUTH_READY=false` until the owner installs approved material and opens an activation window. |
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

## Confirmed business-error envelope

CLICK has confirmed that business-level GetInfo failures return **HTTP 200**
and contain **both** `error` and `error_note`. The route stays disabled while
the catalog mode is `OFF`; this is not an authorization to configure the Click
cabinet, send credentials, send `READY`, or make a payment.

The current numeric error values and note labels below are TEZGO's local
implementation values. CLICK has confirmed the HTTP-200 envelope, not yet an
individual numeric-code catalogue. The implementation never adds `params` to a
business-error response.

| Case | Request fragment | Response example |
| --- | --- | --- |
| Unknown account | `"account":"TZ-9999"` | HTTP `200`, `{"error":-5,"error_note":"ACCOUNT_NOT_FOUND"}` |
| Invalid account | `"account":"9999"` | HTTP `200`, `{"error":-8,"error_note":"MALFORMED_ACCOUNT"}` |
| Blocked/inactive account | `"account":"TZ-0005"` | HTTP `200`, `{"error":-5,"error_note":"ACCOUNT_NOT_ELIGIBLE"}` |
| Service disabled | catalog mode is not `ON`, or `CLICK_GETINFO_ENABLED=false` | HTTP `200`, `{"error":-1,"error_note":"GETINFO_NOT_ENABLED"}` |
| Low-volume rate limit | same direct trusted peer/account exceeds its configured window, or peer exceeds its higher source ceiling | HTTP `200`, `{"error":-8,"error_note":"RATE_LIMITED"}` |
| Temporary business failure | safe internal lookup failure | HTTP `200`, `{"error":-7,"error_note":"TEMPORARY_ERROR"}` |

## Contract acceptance pending

The following must not be represented as accepted CLICK behavior until support
answers in writing. The implementation retains local defensive fallbacks for
malformed/oversized requests, but their status codes are not an external
contract claim and the endpoint remains disabled.

| Pending item | Current position |
| --- | --- |
| Malformed or damaged JSON | **CONTRACT ACCEPTANCE PENDING** |
| Incorrect or missing `Content-Type` | **CONTRACT ACCEPTANCE PENDING** |
| Missing or invalid Basic Auth | **CONTRACT ACCEPTANCE PENDING**; Basic Auth support itself is confirmed, but rejection status/body semantics are not. |
| Temporary-outage retry interval | **CONTRACT ACCEPTANCE PENDING**; a contractually appropriate business failure uses the confirmed HTTP-200 envelope above. |

No real Basic Auth credential has been generated or transmitted. CLICK expects
a sample GetInfo request after implementation; that sample is deferred until
the pending acceptance items and owner authorization are present.

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

Before any cabinet change or deployment, obtain from Click in writing: the
pending malformed-input and Basic-Auth rejection semantics, temporary-outage
retry behavior, the exact numeric error-code catalogue, and the catalog
Prepare/Complete payload examples. Basic Auth support and the HTTP-200
business-error envelope are confirmed, but do not open the activation gate.

The production ingress validation must be read-only and use a synthetic or
owner-approved account only:

1. Once CLICK accepts the pending transport contract, verify `POST
   /api/payment/click-shop/getinfo` reaches the intended host over TLS and
   returns its accepted schema for malformed media/body.
2. Verify a valid authenticated GetInfo request reaches the service, returns
   only `params.full_name`, and makes no DB/Redis/ledger mutation.
3. Verify the canonical `/api/payment/click/prepare` and `/complete` URLs stay
   unchanged and that legacy `/api/payment/click-shop/{prepare,complete}` stay
   denied.
4. Send CLICK's requested sample GetInfo request only after the owner approves
   its contents and the pending contract items are answered. Never include real
   Basic Auth material in source control, a document, or a test fixture.
5. Keep catalog mode `OFF` and `CLICK_GETINFO_AUTH_READY=false` until Click's
   written contract and the owner-approved activation window are both present.
   No real payment is part of this plan.

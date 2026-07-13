# Click Advanced Shop contract gate — 2026-07-13

## Decision received from Click support

- Advanced Shop must use the existing `service_id=105926`; no second service ID
  is to be created or expected.
- The proven driver-app payment path remains unchanged:
  - `POST /api/payment/click/prepare`
  - `POST /api/payment/click/complete`
- The currently supplied GetInfo sample is exactly:

```json
{
  "action": 0,
  "service_id": 105926,
  "params": { "account": "TZ-0005" }
}
```

It does not establish an authentication or signature formula. In particular,
`click_paydoc_id`, `attempt_trans_id`, `sign_time`, and `sign_string` must not
be invented for GetInfo.

## Verified production facts (no secrets printed)

The production environment is configured with `CLICK_SERVICE_ID=105926` and
`CLICK_ADVANCED_SHOP_SERVICE_ID=105926`. Both corresponding secret settings are
present and happen to have equal configured values. This is a configuration
observation only: it does **not** prove that Click expects that secret for
unsigned GetInfo or for any catalog Prepare/Complete request.

The deployed P1 JAR is SHA-256
`0ede62a2a91ed7ea3cffd226839579cb4db1d923d4a3cf03a17f24307c5e9f81`.
It contains the source audited below. No callback configuration was changed and
no Advanced Shop request or payment was initiated for this audit.

## Audit result: current `click-shop` code is not deployable for this decision

| Requirement | Current code | Result |
| --- | --- | --- |
| Keep legacy app Prepare/Complete | `PaymentController.java:86-97` is a separate form-post flow; `PaymentSecurityTest` proves both public callback routes | PASS |
| Existing driver app stays on `/api/payment/create` | `driver-app/src/screens/BalanceScreen.tsx:188-204` obtains backend `clickUrl`, then opens it; no `click-shop` use found | PASS |
| Use `105926` for the proven link | `ClickPaymentTest` now pins `105926` and the generated 1,000 UZS link contract | PASS |
| GetInfo exact account only | `AdvancedShopService.java:94-95` accepts fallback identifiers in addition to `account` | FAIL |
| Trim and uppercase account | `AdvancedShopService.java:197` trims only | FAIL |
| Return only required F.I.Sh. | success response contains `fio`, but `baseResponse` also emits null `click_paydoc_id` and `attempt_trans_id` (`:449-453`) | FAIL |
| Read-only GetInfo | `handleGetinfo` only performs the eager driver lookup | PASS |
| Rate-limit anonymous GetInfo | no `ApiRateLimitService` dependency/call exists in `AdvancedShopController` or `AdvancedShopService` | FAIL |
| No personal data in direct GetInfo logs | no direct GetInfo logging call was found; response still needs its field contract tightened | PARTIAL |
| One credit engine | `AdvancedShopService.creditDriverBalanceInline` duplicates the canonical mutation boundary (`:375-398`) instead of sharing it | FAIL |
| Canonical link order accepted | `PaymentService.handleClickPrepare` only accepts an existing durable `click_payment_orders` order created by `/api/payment/create` | NOT YET EXTENDED |
| Advanced Shop action/payload compatibility | current shop controller assumes JSON actions GetInfo=0, Prepare=1, Complete=2 and a custom signature formula | NOT PROVEN |

The existing code accepts an unsigned GetInfo only when a configured shop secret
is nonblank and `service_id` matches (`AdvancedShopService.java:165-175`). This
is neither a proven Click authentication contract nor abuse-resistant, because
the service ID is not a secret. Do not register or expose this endpoint as a
live Click GetInfo URL until it is redesigned after the confirmed contract.

## Required Click confirmation, verbatim enough to implement

Ask Click support to confirm all of the following in one written response:

1. Can service `105926` retain the existing Prepare and Complete URLs while
   adding only this GetInfo URL?
   `https://137-184-17-18.nip.io/api/payment/click-shop/getinfo`
2. For GetInfo: exact HTTP method, content type, request JSON schema, response
   JSON schema, HTTP/error-code behavior, timeout/retry policy, and whether the
   sample is the complete production request.
3. For GetInfo: whether it is deliberately unsigned; if so, what Click-side
   authentication/IP guarantees apply. If it is signed, provide every field and
   the exact canonical concatenation/encoding formula.
4. Whether the existing Merchant API secret, a different secret, or no secret
   applies to GetInfo. A common service ID is not sufficient evidence.
5. For catalog payments: exact Prepare and Complete URLs, HTTP content type,
   action values, required fields, amount unit/format, identifier field,
   signature formula, expected response fields, idempotency keys, and failure
   behavior. Confirm explicitly whether the existing form callbacks can serve
   them unchanged.
6. Account rules: case sensitivity, whitespace handling, allowed character set,
   F.I.Sh. field name, and the exact error response for an unknown/inactive
   account.

## Implementation gate after confirmation

Only after the answers above are received:

1. Preserve the legacy controller mappings exactly as they are.
2. Implement GetInfo as a strict, read-only endpoint: `action=0`, service
   `105926`, `params.account` only, trim+uppercase normalization, exact public
   driver-code lookup, minimal F.I.Sh. response, and IP/account rate limiting.
3. Add a catalog-payment adapter to the canonical durable order/Click
   transaction path, or extract a single shared wallet-credit boundary. It must
   not retain `AdvancedShopService`'s separate inline TOPUP mutation engine.
4. Add contract fixtures supplied by Click; do not manufacture signature
   fixtures. Include regression tests for the existing app link flow, GetInfo
   privacy/rate-limit behavior, catalog retries, duplicate Complete, and the
   P1 ledger snapshot chain.
5. Run the full suite and obtain Click's written callback configuration approval
   before a deployment or callback-portal change.

## Tests executed for this decision

- `mvnw.cmd -q -Dtest=ClickPaymentTest,PaymentSecurityTest,AdvancedShopServiceTest test`
  — 57 tests, 0 failures, 0 errors, 0 skipped.
- After adding the legacy link regression:
  `mvnw.cmd -q -Dtest=ClickPaymentTest,PaymentSecurityTest test`
  — 29 tests, 0 failures, 0 errors, 0 skipped.
- Full backend gate after the change: `mvnw.cmd -q test` — 378 tests, 0
  failures, 0 errors, 7 Testcontainers skips because Docker Desktop is not
  available. `mvnw.cmd -q -DskipTests package` also succeeded.

## Interim verdict

**ADVANCED SHOP CONTRACT PENDING — SERVICE ID CONFIRMED**

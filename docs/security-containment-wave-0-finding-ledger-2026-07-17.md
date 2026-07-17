# TEZGO Admin Security Containment Wave 0 — Finding Ledger

Date: 2026-07-17
Scope: current production-proven findings only; no production mutation

## Candidate

- Worktree: `C:\Users\Laptop\Documents\tezgo-admin-security-containment`
- Branch: `fix/admin-security-containment-wave-0`
- Base/HEAD: `38e1060b6c760cafea31baaccd1c85b8f4e52b61`
- Canonical backend: `C:\Users\Laptop\Documents\tezgo-backend-ao-click-integration`
- No database migration added. Existing Flyway `V49` and `V50` remain present.

## Complete 35-finding ledger

| Finding IDs | Official lineage/status | Wave 0 disposition | Evidence/control |
|---|---|---|---|
| DS-001, DS-002 | Current production proven | **FIXED IN CANDIDATE** | Public OTP role boundary accepts end-user roles only; `ADMIN`/`OPERATOR` are rejected. Staff roles require the authenticated staff workflow. `AuthControllerContainmentTest`, `AuthServiceContainmentTest`. |
| DS-005 | Current production proven | **FIXED IN CANDIDATE** | OTP monitor is ADMIN-only and returns masked phone metadata with `code=null`; live OTP is never serialized. `AdminOtpMonitorContainmentTest`. |
| DS-006 | Current production proven | **FIXED IN CANDIDATE** | OTP generation, delivery, mismatch and review logs no longer contain phone/code values. Secret scan found no credential-shaped matches in changed source/test files. |
| DS-007 | Current production proven | **FIXED IN CANDIDATE** | HTTP JWT authority is bound to the current database role; stale role claims are denied. `JwtFilterContainmentTest`. |
| DS-008 | Current production proven | **FIXED IN CANDIDATE** | STOMP CONNECT requires an access token and current database role; refresh/stale-role sessions are denied. Existing subscription allowlist remains intact. `WebSocketAuthInterceptorTest`. |
| DS-025 | Current production proven | **FIXED IN CANDIDATE** | Government/provider logs are redacted and response allowlists omit restricted identity fields. |
| DS-026 | Current production proven | **FIXED IN CANDIDATE** | Driver registration is bound to the authenticated DRIVER subject, ignores body phone, uses a pessimistic existing-profile check, and rejects replay/cross-profile mutation. `AuthControllerContainmentTest`, `AuthServiceContainmentTest`. |
| DS-036 | Current production proven | **FIXED IN CANDIDATE** | Passport verification requires an authenticated DRIVER and returns only an explicit allowlist. `AuthControllerContainmentTest`. |
| DS-037 | Current production proven | **FIXED IN CANDIDATE** | Vehicle verification requires an authenticated DRIVER, has no fixture fallback, and omits owner PII. `AuthControllerContainmentTest`. |
| DS-039 | Current production proven | **FIXED IN CANDIDATE** | Provider fixture fallback was removed; missing provider data returns no synthetic identity/vehicle result. |
| DS-040 | Current production proven | **FIXED IN CANDIDATE** | Anonymous/self-credit wallet top-up endpoint and service path were removed; canonical ledger/payment paths are unchanged. Existing payment/Click tests remain green. |
| DS-047 | Current production proven | **FIXED IN CANDIDATE** | Client-supplied fare/distance is ignored for authoritative finish/continue calculations; server coordinates and Haversine distance are used. Nearby map coordinates are coarse-rounded. `Taxometer*Test`, `PassengerNearbyContainmentTest`. |
| DS-003, DS-004, DS-010 | Current source proven; runtime unproven | **OPEN / VALIDATED SOURCE-ONLY** | No code change in Wave 0. Safe local source/config inspection did not establish the missing production runtime/provider evidence. |
| DS-027, DS-028, DS-029, DS-030, DS-042, DS-043, DS-044, DS-056 | Runtime/provider evidence required | **OPEN / NOT CHANGED** | No provider credentials, OTP, PII, balance, payment, trip, broadcast, or production runtime action was performed. |
| DS-012, DS-013, DS-014, DS-015, DS-016, DS-020, DS-021, DS-022, DS-031, DS-048 | Historical resolved | **REGRESSION ONLY** | Not reimplemented; existing regression coverage was retained and full backend suite passed. |
| DS-023 | Official false positive | **NO CHANGE** | No new data-minimization change was required for this finding. |

## Non-negotiable preserved contracts

AO-P1-01 STOMP behavior, AO-P1-02 idempotency, AO-P1-03 sequential offers, Flyway V49/V50, Click app-link/catalog behavior, ledger snapshot behavior, and WebSocket authorization were not intentionally redesigned.

## Gate interpretation

“FIXED IN CANDIDATE” means the source control and focused regression evidence passed locally. It is not a production deployment claim. Production approval and runtime/provider evidence remain separate gates.

# TEZGO Admin Security Containment Wave 0 — Candidate Report

Date: 2026-07-17
Verdict: `ADMIN SECURITY CONTAINMENT INCOMPLETE — BLOCKERS REMAIN`

## Scope and safety

This is a remediation candidate only. The canonical backend, Admin source tree, production JAR, production database, and audit worktree were not modified. No deployment, payment, trip, broadcast, OTP, PII, balance, or provider-secret operation was performed.

The complete 35-ID accounting is in [security-containment-wave-0-finding-ledger-2026-07-17.md](security-containment-wave-0-finding-ledger-2026-07-17.md).

## Backend candidate

- Worktree: `C:\Users\Laptop\Documents\tezgo-admin-security-containment`
- Branch: `fix/admin-security-containment-wave-0`
- Base/HEAD: `38e1060b6c760cafea31baaccd1c85b8f4e52b61`
- Production-equivalent reference JAR: `C:\Users\Laptop\Documents\tezgo-backend-ao-click-integration\artifacts\tezgo-backend-ao-v49-click-v50-clockfix-38e1060-20260716-154455.jar`
- Reference JAR SHA-256: `2EB3E1559B1EB2FCBA7EE7B1B7F55314F13AFF5A5AD373D02966FDA13341AABB`
- Candidate JAR: `C:\Users\Laptop\Documents\tezgo-admin-security-containment\target\backend-1.0.0.jar`
- Candidate JAR size: `154,113,860` bytes
- Candidate JAR SHA-256: `19012F2330D0AE19FD1F1823A848A1E46208AD875D0CEE3BDA9F094596E82C9C`
- Candidate inventory contains `AuthController`, `AdminController`, `JwtFilter`, `WebSocketAuthInterceptor`, `GeoDistance`, `TaxometerService`, `TripService`, `V49__create_trip_driver_offers.sql`, and `V50__add_click_superapp_catalog_payment_state.sql`.
- No new migration was added; production Flyway V50 remains the target.

## Changes and outcomes

The candidate closes the 13 current production-proven IDs listed in the ledger through public-role containment, OTP redaction/permission narrowing, current-role JWT/WS binding, authenticated driver ownership and replay protection, KYC response minimization/no fixture fallback, removal of anonymous wallet self-credit, server-authoritative fare/distance, and coarse nearby-map coordinates.

The three current-source-proven/runtime-unproven IDs (DS-003, DS-004, DS-010) remain open because the required production runtime evidence was not available. The eight provider/runtime findings remain unchanged by instruction. Historical resolved findings were not reimplemented.

## Validation receipts

- `\.\mvnw.cmd test`: **468 tests, 0 failures, 0 errors, 25 skipped**, `BUILD SUCCESS`.
- `\.\mvnw.cmd clean package`: **468 tests, 0 failures, 0 errors, 25 skipped**, `BUILD SUCCESS`.
- Focused containment tests: pass, including `AuthControllerContainmentTest`, `AuthServiceContainmentTest`, `AdminOtpMonitorContainmentTest`, `JwtFilterContainmentTest`, `WebSocketAuthInterceptorTest`, `PassengerNearbyContainmentTest`, and authoritative taxometer tests.
- Critical repeat slice: `AuthServiceContainmentTest` + `TaxometerFinishIdempotentTest`, **20/20 pass**.
- Existing Click/payment, ledger, STOMP, idempotency, sequential-offer, and Flyway regression suites remained green where runnable.
- `git diff --check`: pass; only normal LF/CRLF normalization warnings were emitted.
- Secret scan over changed source/test files: `NO_MATCHES` for private-key, cloud-key, token, and credential-shaped patterns.
- Testcontainers/Postgres migration and concurrency slices were skipped because no valid Docker environment was available. Their production-like DB proof is therefore **unknown**, not inferred.

## Admin V2 frontend baseline

- Clean worktree: `C:\Users\Laptop\Documents\tezgo-admin-v2-frontend-baseline`
- Branch: `fix/admin-security-containment-wave-0-admin-baseline`
- Source HEAD: `a68498eb32daad2ac863da2bbdc62b76a6b0c012`
- Admin path: `admin-panel`
- `npm ci`: pass; npm reported 8 dependency audit findings (1 low, 3 moderate, 4 high). No `npm audit fix` was applied.
- `npm run lint`: fail, **196 errors and 40 warnings**.
- `npx tsc --noEmit`: pass after clean install.
- Normal `npm run build`: blocked by the repository lockfile missing the platform SWC metadata expected by Next 14.2.35 (`patch-incorrect-lockfile` failure).
- Diagnostic build with `NEXT_IGNORE_INCORRECT_LOCKFILE=1`: pass; 20 static routes generated; build ID `BZbwXxcCwG0FNx5hj2A-x`. The override was local-only and no tracked source was changed.

Frontend backlog (not implemented in Wave 0): the 196 lint errors, 40 warnings, stale/generated type hygiene, missing Admin tests, token/session hardening, CSP, OTP UI masking, and PII masking review.

## Deployment and rollback plan (not executed)

Deployment is explicitly blocked pending the exact approval phrase `SECURITY CONTAINMENT DEPLOY APPROVED`, production runtime/provider evidence for the open IDs, and a Docker-backed migration/concurrency gate. If approved later, the release must use the candidate JAR above, verify its SHA-256 immediately before transfer, run the existing health/auth/Click/WS smoke gates, and retain the current production JAR with reference SHA `2EB3E1559B1EB2FCBA7EE7B1B7F55314F13AFF5A5AD373D02966FDA13341AABB` for rollback. No rollback or production command was run in Wave 0.

## Hard stop

Do not deploy or mutate production from this candidate. The remaining runtime/provider evidence, DB-backed concurrency/migration proof, frontend lint backlog, and explicit deployment approval are blockers.

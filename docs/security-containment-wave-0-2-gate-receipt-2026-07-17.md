# TEZGO Admin Security Containment Wave 0.2 — Gate Receipt

Date: 2026-07-17  
Scope: read-only release evidence; no production deployment, production database mutation, provider call, payment, trip, OTP, KYC, PII or secret exposure.

## Provenance

- Worktree: `C:\Users\Laptop\Documents\tezgo-admin-security-containment`
- Branch before this receipt commit: `fix/admin-security-containment-wave-0`
- Source/remediation commit: `b58a91bfb3953cdf9634bc138d88131e666cb39d`
- Receipt-only branch tip before this commit: `9d73a10ef0dfd94a2506c0b42d593bcab9d5bde2`
- `git show --stat b58a91b..HEAD`: documentation-only commits (`a12d83a`, `43534f4`, `7fd62f1`, `fcac161`, `9d73a10`); no source, test or migration changes after `b58a91b`.
- Existing Wave 0.1 JAR was built from `b58a91b`, not the receipt tip, and is not reused.
- Existing Wave 0.1 bundle points to `b58a91b`, and is not reused.

## Real Hibernate validation

The disposable PostgreSQL target was `postgres:16-alpine`, PostgreSQL `16.14`, on host port `55433`; Redis was disposable on `16379`. The candidate uses Hibernate `6.6.8.Final`.

| Scenario | Flyway | JPA mode | Result | Receipt |
|---|---:|---|---|---|
| Empty bootstrap | V1→V50 | `validate` | **FAIL** — context exits 1 | `logs/wave02-clean-startup.out.log`, SHA-256 `415AF570F2157F5462CC37B1BF5AB1A1AEA9AE262861740DBD56470702EA31E2` |
| V50 fixture with sanitized users, driver, trip, transaction, Click order/transaction, idempotency and offer rows | V50 | `validate` | **FAIL** — process exits 1 | `logs/wave02-v50-validate-exit.log`, SHA-256 `C87E879DCE244AFEEDF16723CB988AF6D1BAA0A96899BCD7ECF05A1F0E60F6E8` |

Both failures are the same schema mismatch:

`trips.distance_km`: PostgreSQL `float8` / JDBC `DOUBLE`; Hibernate mapping expects `numeric(8,2)` / JDBC `NUMERIC`.

No post-V50 migration exists. The mismatch is a real production-like blocker, not an H2 or test-property artifact. No source fix was invented in this containment wave.

## Race matrix

The machine-readable receipt is [`security-containment-wave-0-2-race-receipt-2026-07-17.json`](security-containment-wave-0-2-race-receipt-2026-07-17.json). Real PostgreSQL/Testcontainers tests pass for the existing repeated duplicate-credit and one-live-offer families. Ten required families are explicitly `NOT_PROVEN`; no result is inflated from deterministic unit coverage to a 20-run database claim.

- Duplicate wallet credit: 20/20, 20 workers per repetition, pass.
- Duplicate valid payment Complete: 20/20, 20 workers per repetition, pass.
- AO one-live-offer invariant: 20/20, two workers per repetition, pass.
- Operator idempotency: one 20-worker race pass, but not 20 independent repetitions.
- All other required families: deterministic or single-run evidence only; `NOT_PROVEN`.

## Frontend compatibility

Clean Admin worktree: `C:\Users\Laptop\Documents\tezgo-admin-v2-frontend-baseline\admin-panel`, branch `fix/admin-security-containment-wave-0-admin-baseline`, HEAD `a68498eb32daad2ac863da2bbdc62b76a6b0c012`.

- `npm ci --ignore-scripts --no-audit --fund=false`: exit 0 after a retry caused by a transient Windows `ENOTEMPTY` dependency-directory condition.
- `npx tsc --noEmit`: exit 0.
- `npm run lint`: exit 1, existing 196 errors and 40 warnings; no mechanical lint rewrite made.
- Normal `npm run build`: route generation completes, but Next 14.2.35 repeatedly reports the lockfile missing platform SWC dependency metadata and fails its lockfile patch attempt. The unsupported `NEXT_IGNORE_INCORRECT_LOCKFILE=1` diagnostic bypass was not used as final proof.
- OTP/KYC response consumers were inspected; no Admin compatibility patch was applied in this wave.

Decision: **BACKEND-ONLY DEPLOY COMPATIBLE — NOT APPROVED**, pending normal frontend build-environment repair and the backend blockers above.

## Full backend quality gate

`\.\mvnw.cmd test` exited 0: 506 tests, 0 failures, 0 errors, 0 skipped; Surefire XML set 102 files, deterministic concatenated XML SHA-256 `128E7AD6DA747EBB8B8C27E0097C4713994B5397E983919360248BA32BBF5E2D`.

`git diff --check` passed. The clean package and immutable final artifact are intentionally deferred until this receipt is committed, so the final candidate can be built from one exact committed HEAD.

## Fixed findings and open classifications

The 13 Wave 0 fixed IDs remain: DS-001, DS-002, DS-005, DS-006, DS-007, DS-008, DS-025, DS-026, DS-036, DS-037, DS-039, DS-040 and DS-047. Current source/test paths were re-read; no unrelated source or migration change is present.

- DS-003, DS-004, DS-010: **D — CURRENT PRODUCTION EVIDENCE STILL MISSING**.
- DS-027, DS-028, DS-029, DS-030, DS-042, DS-043, DS-044, DS-056: **DEPLOYMENT BLOCKER — EVIDENCE MISSING**; no provider/runtime secret or PII evidence was accessed.
- No current production P1 was silently reclassified as resolved.

## Gate state

The true Hibernate validation failure, incomplete 20-run matrix, and unresolved normal Admin build environment are release blockers. Build/package/artifact and offline bundle must follow this receipt commit and must not reuse the Wave 0.1 checkpoint.

**ADMIN SECURITY CONTAINMENT INCOMPLETE — BLOCKERS REMAIN**

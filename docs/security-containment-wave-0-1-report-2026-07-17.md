# TEZGO Admin Security Containment Wave 0.1 — final gate report

Date: 2026-07-17  
Verdict: **INCOMPLETE — blockers remain**

This is a read-only release-gate report for the isolated candidate. No production database, runtime, provider, payment callback, OTP, KYC, PII, balance, trip, location, deployment, or source mutation was performed.

## A–U gate receipt

### A. Branch, HEAD, and preservation

- Backend worktree: `C:\Users\Laptop\Documents\tezgo-admin-security-containment`
- Branch: `fix/admin-security-containment-wave-0`
- HEAD: `b58a91bfb3953cdf9634bc138d88131e666cb39d`
- Parent: `38e1060b6c760cafea31baaccd1c85b8f4e52b61`
- Working-tree changes before this receipt were the pre-existing untracked `logs/` only; the committed remediation was not rewritten.
- Driver compatibility worktree remained separate at commit `79f4bf1c1232f36c2f2b2c7daea400ec87b957ee`.

### B. Remote branch and history scan

- Remote: `origin` (`SarvarUrinboyev/tezgo-backend`).
- Branch exists remotely at `b58a91bfb3953cdf9634bc138d88131e666cb39d`.
- Only `fix/admin-security-containment-wave-0` was pushed; no merge, main-branch update, force-push, PR, or deployment was performed.
- High-confidence secret scan covered 77 reachable commits: 0 hit commits and 0 hit paths. Tracked risk inventory contained only `.env.example`; no tracked JAR, dump, log, environment secret, private key, or credential file.

### C. Surefire reconciliation

| Snapshot | XML reports | Tests | Passed | Skipped | Failed | Errors |
|---|---:|---:|---:|---:|---:|---:|
| Pre-Docker | 102 | 468 | 443 | 25 | 0 | 0 |
| Earlier “493” receipt | — | 493 | — | — | — | — |
| Canonical baseline | 97 | 490 | 490 | 0 | 0 | 0 |
| Final Docker/PostgreSQL | 102 | 506 | 506 | 0 | 0 | 0 |

The “493” number was an arithmetic error: the pre-Docker 468 already included the 25 skipped cases. Final execution expanded those 25 skipped method entries to 63 passing PostgreSQL testcases, producing 506 total. The final XML-set SHA-256 is `89266DA0E4E92EB904E6244F713990CDDB7C1939D8CDCCFBC04BC9AA5FFA6D7C`.

### D. Complete skipped-test inventory

The complete 506-row Surefire inventory and all 25 previously skipped entries are in `security-containment-wave-0-skipped-test-inventory-2026-07-17.md`. The machine-readable command receipt is `security-containment-wave-0-skipped-test-receipt-2026-07-17.json`.

### E. Docker/PostgreSQL

- Docker Desktop was started locally only; Docker client/server `29.6.1`, Testcontainers `1.21.4`, `postgres:16-alpine`, PostgreSQL `16.14`.
- A disposable local PostgreSQL path on host port 55433 was used for readiness; Testcontainers used its own disposable ports and Ryuk cleanup. Existing `tezgo-pg` and `qalqon-pg` containers were not modified.

### F. Previously skipped tests

Exact focused command exited 0 in `02:11`: 63 run, 63 passed, 0 skipped, 0 failure, 0 error. It covered schema bootstrap, V47–V50 assertions, Click catalog/payment races, ledger rollback, operator idempotency, payment ledger snapshots, and live-offer uniqueness.

### G. Migration proof

- `git diff b58a91b^ b58a91b -- src/main/resources/db/migration` is empty; no migration was added.
- The candidate contains V50 and clean bootstrap applied V1 through V50 on PostgreSQL 16.14; V49/V50, V47 idempotency, V48 request-hash mapping, and V50 catalog constraints were asserted.
- Candidate startup context and Flyway bootstrap passed in the Testcontainers Spring slice.
- **Hibernate validation is not accepted as proven:** `SchemaMigrationBootstrapTest` pins `spring.jpa.hibernate.ddl-auto=none` (documented in the test), so the attempted `-Dspring.jpa.hibernate.ddl-auto=validate` run exited 0 without overriding that higher-precedence test property. A true production-schema Hibernate `validate` receipt is still required.

### H. Repeated concurrency

- Duplicate catalog completion/wallet credit: `@RepeatedTest(20)` with 20 concurrent contenders per invocation (400 contenders), passed.
- One-live-offer admission: `@RepeatedTest(20)` with two simultaneous inserts per invocation, passed.
- Operator idempotency: 20 parallel same-key requests in one database race, one trip and 19 replays, passed.
- Click payment race and authoritative ledger snapshot tests passed, but the ledger snapshot and two-contender Click slice were not independently repeated 20 times. KYC/profile and role-enrollment checks are deterministic containment tests, not 20-run database races. This gate is therefore **partial**, not a release approval.

### I. DS-003, DS-004, DS-010 classifications

All three are **D — RUNTIME EVIDENCE STILL MISSING**. Current source paths and candidate classes are present, but production bootstrap values, first-run state, and `TOKEN_BLACKLIST_FAIL_CLOSED` runtime state were not read. They are not classified as production-affected without that evidence, and they are not classified as “not affected.”

### J. Runtime/provider classifications

DS-027, DS-028, DS-029, DS-030, DS-042, DS-043, DS-044, and DS-056 are each **D — RUNTIME EVIDENCE STILL MISSING**. Safe source/JAR/config-name evidence is present; production provider secrets, Nginx/TLS listeners, Grafana/Prometheus exposure, and aggregate runtime logs were not inspected. No callback, credential, or exploit was sent.

### K. Fixed finding IDs

The candidate fixes exactly these 13 IDs: **DS-001, DS-002, DS-005, DS-006, DS-007, DS-008, DS-025, DS-026, DS-036, DS-037, DS-039, DS-040, DS-047**. The independent diff review found only the intended backend controls, associated tests, and receipt docs; no migration or unrelated product feature was changed.

### L. Open findings/blockers

- DS-003, DS-004, DS-010: runtime classification D.
- Eight runtime/provider IDs in J: classification D; DS-029/030/042 remain P1 source paths pending provider/runtime evidence.
- True Hibernate schema validation is unproven.
- Admin frontend normal build is blocked by the incorrect-lockfile/SWC metadata issue; lint has 196 errors and 40 warnings.
- Independent 20-run coverage for ledger, Click two-contender, KYC/profile, and role-enrollment scenarios is not proven.

### M. Full test/package totals

- `\.\mvnw.cmd test`: exit 0, `02:20`, 506 passed, 0 skipped/failure/error.
- `\.\mvnw.cmd clean package`: exit 0, `02:41`, 506 passed, 0 skipped/failure/error; package produced a new candidate JAR.
- `git diff --check`: pass.

### N. Admin V2 compatibility

Admin was kept separate and unmodified: worktree `C:\Users\Laptop\Documents\tezgo-admin-v2-frontend-baseline`, branch `fix/admin-security-containment-wave-0-admin-baseline`, HEAD `a68498eb32daad2ac863da2bbdc62b76a6b0c012`. `npm ci` passed (8 audit findings: 1 low, 3 moderate, 4 high); `npx tsc --noEmit` passed; diagnostic `NEXT_IGNORE_INCORRECT_LOCKFILE=1 npm run build` passed with 20 routes; normal build failed on Next 14.2.35 lockfile/SWC metadata; lint failed with 196 errors and 40 warnings. No Admin release is approved. Required order remains backend candidate and driver compatibility review first, then a separately approved Admin release.

### O. Immutable JAR

- Path: `artifacts\tezgo-admin-security-wave-0-1-b58a91b-20260717T110429Z.jar`
- Size: 154,113,860 bytes; read-only set.
- SHA-256: `AFA930BE4624B95E385307DBB39733B0601985317A9D5C5D48DC22221F3F086D`
- Built from HEAD `b58a91b`; migration head V50; class inventory 648 entries; route inventory 183 entries.
- Prior artifact hash `19012F...E82C9C` was not reused. Production-reference JAR hash remains `2EB3E1559B1EB2FCBA7EE7B1B7F55314F13AFF5A5AD373D02966FDA13341AABB`.

### P. Offline Git bundle

- Path: `artifacts\admin-security-containment-wave-0-1-b58a91b.bundle`
- `git bundle verify`: complete history, ref `b58a91bfb3953cdf9634bc138d88131e666cb39d`.
- SHA-256: `ACA4E2707EC859986A45873A04BA91FEA4E92B442AB3AD040BFE9F1E516187FC`; size 648,978 bytes.

### Q. Independent read-only review

Fresh review of the commit diff, candidate JAR inventory, route inventory, branch/ref, Surefire XML set, and high-confidence secret scan passed. No production source/worktree, tracked secret, migration, or unrelated API feature was found. Review did identify the unproven Hibernate validation and partial 20-run race coverage recorded above.

### R. Deployment decision

**Do not deploy.** Approval is blocked by runtime/provider evidence, true Hibernate validation, partial repeated-race coverage, Admin lint/normal-build blockers, and the absence of the explicit deployment approval phrase.

### S. Rollback

Rollback boundary is the retained production-reference JAR SHA `2EB3E1559B1EB2FCBA7EE7B1B7F55314F13AFF5A5AD373D02966FDA13341AABB`, plus the offline bundle and branch ref. No rollback action was executed.

### T. Git status

After committing this receipt, only intentionally untracked local artifacts (`artifacts/`) and pre-existing `logs/` remain outside the tracked documentation. JARs, bundle, logs, and metadata are not staged.

### U. Production actions

**NONE.** No production command, database, provider, credential, user, payment, trip, dispatch, location, OTP, KYC, PII, deployment, or rollback was touched.

## Final verdict

**INCOMPLETE — blockers remain.**

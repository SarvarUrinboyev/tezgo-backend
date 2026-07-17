# Admin V2 Frontend Baseline — Wave 0

- Clean worktree: `C:\Users\Laptop\Documents\tezgo-admin-v2-frontend-baseline`
- Branch: `fix/admin-security-containment-wave-0-admin-baseline`
- Source HEAD: `a68498eb32daad2ac863da2bbdc62b76a6b0c012`
- Project: `admin-panel`

## Commands

1. `npm ci` — pass; npm reported 8 audit findings (1 low, 3 moderate, 4 high). No fixes applied.
2. `npm run lint` — fail: **196 errors, 40 warnings**.
3. `npx tsc --noEmit` — pass after clean install.
4. `npm run build` — normal run blocked by Next 14.2.35 incorrect-lockfile/SWC metadata failure.
5. `NEXT_IGNORE_INCORRECT_LOCKFILE=1 npm run build` — pass; 20 static routes; build ID `BZbwXxcCwG0FNx5hj2A-x`.

The diagnostic environment variable was not persisted and no tracked Admin source was changed. No Admin redesign was attempted.

## Backlog

Address the 196 lint errors and 40 warnings, stale/generated type hygiene, missing tests, token/session handling, CSP, OTP UI masking, and PII masking before an Admin release gate.

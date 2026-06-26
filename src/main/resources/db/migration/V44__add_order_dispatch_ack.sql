-- Layer 2c — order-alert delivery ACK backbone.
--
-- Two per-trip timestamps that let Layer 2b escalate ONLY when an order
-- genuinely reached no one (the background/killed FCM-failure case), without
-- false-escalating when a driver engaged via any path.
--
--   dispatched_at      = when the order was actually dispatched to drivers
--                        (set at notify time; NOT booking time — matters for
--                        SCHEDULED trips that sit before dispatch).
--   first_received_at  = when the FIRST notified driver's app DISPLAYED the
--                        order (IncomingOrderModal-visible ACK). NULL = no
--                        driver's app ever showed it.
--
-- ADDITIVE ONLY. Does not touch the data-only push contract or the native FSI
-- chain. These columns are read by the (separate) Layer 2b escalation sweep:
--   escalate IF status='SEARCHING'
--          AND dispatched_at < now() - 25s
--          AND first_received_at IS NULL          -- no app displayed it
--          AND (excluded_driver_ids IS NULL OR excluded_driver_ids = '')  -- nobody declined
-- (accept moves the trip out of SEARCHING, so accept is covered by the status filter.)

ALTER TABLE trips ADD COLUMN IF NOT EXISTS dispatched_at     TIMESTAMP;
ALTER TABLE trips ADD COLUMN IF NOT EXISTS first_received_at TIMESTAMP;

-- Partial index for the escalation sweep: only un-received trips matter.
CREATE INDEX IF NOT EXISTS idx_trips_dispatch_escalation
    ON trips (status, dispatched_at)
    WHERE first_received_at IS NULL;

-- ============================================================
-- V6: Yetishmayotgan ustunlar va sxema sinxronizatsiyasi
-- Entity va migration orasidagi nomuvofiqliklar tuzatiladi
-- ============================================================

-- Trips: source, waiting fields
ALTER TABLE trips ADD COLUMN IF NOT EXISTS source VARCHAR(50);
ALTER TABLE trips ADD COLUMN IF NOT EXISTS waiting_started_at TIMESTAMP;
ALTER TABLE trips ADD COLUMN IF NOT EXISTS waiting_ended_at TIMESTAMP;
ALTER TABLE trips ADD COLUMN IF NOT EXISTS waiting_price BIGINT DEFAULT 0;
ALTER TABLE trips ADD COLUMN IF NOT EXISTS trip_waiting_started_at TIMESTAMP;
ALTER TABLE trips ADD COLUMN IF NOT EXISTS trip_waiting_ended_at TIMESTAMP;
ALTER TABLE trips ADD COLUMN IF NOT EXISTS trip_waiting_price BIGINT DEFAULT 0;
ALTER TABLE trips ADD COLUMN IF NOT EXISTS surge_multiplier DOUBLE PRECISION DEFAULT 1.0;
ALTER TABLE trips ADD COLUMN IF NOT EXISTS promo_code VARCHAR(30);
ALTER TABLE trips ADD COLUMN IF NOT EXISTS promo_discount BIGINT DEFAULT 0;

-- Broadcast Messages: sent_by, target
ALTER TABLE broadcast_messages ADD COLUMN IF NOT EXISTS sent_by BIGINT REFERENCES users(id);
ALTER TABLE broadcast_messages ADD COLUMN IF NOT EXISTS target VARCHAR(50) DEFAULT 'ALL';

-- Promo Codes: created_at
ALTER TABLE promo_codes ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- Transactions: balance_before va balance_after NOT NULL default
ALTER TABLE transactions ALTER COLUMN balance_before SET DEFAULT 0;
ALTER TABLE transactions ALTER COLUMN balance_after SET DEFAULT 0;

-- Performance indexes
CREATE INDEX IF NOT EXISTS idx_trips_completed_at ON trips(completed_at) WHERE status = 'COMPLETED';
CREATE INDEX IF NOT EXISTS idx_trips_driver_status ON trips(driver_id, status);
CREATE INDEX IF NOT EXISTS idx_trips_passenger_status ON trips(passenger_id, status);
CREATE INDEX IF NOT EXISTS idx_transactions_driver_type ON transactions(driver_id, type);
CREATE INDEX IF NOT EXISTS idx_broadcast_sent_at ON broadcast_messages(sent_at DESC);

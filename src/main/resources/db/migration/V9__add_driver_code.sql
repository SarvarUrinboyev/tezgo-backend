-- ============================================================
-- Driver Code (TZ-XXXX) — unique human-readable driver ID
-- Concurrency-safe: DB sequence guarantees atomicity
-- ============================================================

-- 1. Sequence — atomik kod generatsiya uchun
CREATE SEQUENCE IF NOT EXISTS driver_code_seq START WITH 1 INCREMENT BY 1 NO CYCLE;

-- 2. Ustun qo'shish (backfill uchun avval nullable)
ALTER TABLE drivers ADD COLUMN IF NOT EXISTS driver_code VARCHAR(20);

-- 3. Mavjud haydovchilarga kod berish (id tartibida)
DO $$
DECLARE
    rec    RECORD;
    num    BIGINT;
    code   VARCHAR(20);
BEGIN
    FOR rec IN SELECT id FROM drivers ORDER BY id ASC LOOP
        num  := nextval('driver_code_seq');
        code := 'TZ-' || LPAD(num::TEXT, 4, '0');
        UPDATE drivers SET driver_code = code WHERE id = rec.id;
    END LOOP;
END $$;

-- 4. NOT NULL + UNIQUE constraint
ALTER TABLE drivers ALTER COLUMN driver_code SET NOT NULL;
ALTER TABLE drivers ADD CONSTRAINT uq_drivers_driver_code UNIQUE (driver_code);
CREATE INDEX IF NOT EXISTS idx_drivers_driver_code ON drivers(driver_code);

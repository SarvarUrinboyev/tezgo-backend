-- Ikki tomonlama reyting: yo'lovchi↔haydovchi (P2D = passenger→driver, D2P = driver→passenger).
-- ratings jadvali bo'sh edi — xavfsiz.

-- Yo'nalish + baholanayotgan yo'lovchi (D2P uchun)
ALTER TABLE ratings ADD COLUMN IF NOT EXISTS direction VARCHAR(8) NOT NULL DEFAULT 'P2D';
ALTER TABLE ratings ADD COLUMN IF NOT EXISTS to_user_id BIGINT REFERENCES users(id);

-- D2P reytingda to_driver_id bo'sh bo'ladi
ALTER TABLE ratings ALTER COLUMN to_driver_id DROP NOT NULL;

-- Har safarga 1 ta emas, har yo'nalish bo'yicha 1 ta reyting
ALTER TABLE ratings DROP CONSTRAINT IF EXISTS ratings_trip_id_key;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ratings_trip_direction_key') THEN
    ALTER TABLE ratings ADD CONSTRAINT ratings_trip_direction_key UNIQUE (trip_id, direction);
  END IF;
END $$;

-- Yo'lovchi o'rtacha reytingi (drivers.rating ga o'xshash: numeric(3,2))
ALTER TABLE users ADD COLUMN IF NOT EXISTS rating NUMERIC(3,2) NOT NULL DEFAULT 5.0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS rating_count INTEGER NOT NULL DEFAULT 0;

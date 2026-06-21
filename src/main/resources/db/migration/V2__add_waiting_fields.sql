-- Kutish (waiting) funksiyasi uchun yangi ustunlar
ALTER TABLE trips ADD COLUMN IF NOT EXISTS waiting_started_at TIMESTAMP;
ALTER TABLE trips ADD COLUMN IF NOT EXISTS waiting_ended_at TIMESTAMP;
ALTER TABLE trips ADD COLUMN IF NOT EXISTS waiting_price BIGINT DEFAULT 0;

-- Haydovchi yetib kelgan vaqt (DRIVER_ARRIVED) — pullik kutishni hisoblash uchun
-- server tomonidagi authoritative anchor. Kutish narxi shu vaqtdan boshlab
-- (60s bepul, keyin har soniya) hisoblanadi.
ALTER TABLE trips ADD COLUMN IF NOT EXISTS arrived_at TIMESTAMP;

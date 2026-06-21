-- free_since: haydovchi oxirgi marta bo'sh bo'lgan vaqt (online yoki safar yakuni) —
--   matching tenglik (tie-break) uchun "navbatda eng erta bo'shagani g'olib".
-- order_cooldown_until: rad etishdan keyin shu vaqtgacha yangi buyurtma berilmaydi.
ALTER TABLE drivers ADD COLUMN IF NOT EXISTS free_since TIMESTAMP;
ALTER TABLE drivers ADD COLUMN IF NOT EXISTS order_cooldown_until TIMESTAMP;

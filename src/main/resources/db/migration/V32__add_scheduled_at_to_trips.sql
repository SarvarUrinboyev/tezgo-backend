-- Rejalashtirilgan buyurtma (scheduled ride): mijoz keyingi vaqtga taksi chaqiradi.
-- scheduled_at NULL bo'lsa — oddiy (darhol) buyurtma. To'lдirilgan bo'lsa — vaqti kelganda
-- scheduler uни SEARCHING ga o'tkazib, haydovchilarga yuboradi.
ALTER TABLE trips ADD COLUMN scheduled_at TIMESTAMP;

-- Scheduler so'rovi uchun indeks (SCHEDULED + vaqti kelgan triplar).
CREATE INDEX idx_trips_scheduled_at ON trips (scheduled_at) WHERE scheduled_at IS NOT NULL;

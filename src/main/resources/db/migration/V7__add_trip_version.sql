-- Optimistic Lock uchun version ustun
-- @Version annotatsiyasi bilan ishlaydi: UPDATE trips SET ... WHERE id=? AND version=?
-- Agar version mos kelmasa — OptimisticLockException (boshqa haydovchi oldin qabul qilgan)
ALTER TABLE trips ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;

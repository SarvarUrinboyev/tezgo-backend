-- Haydovchi bekor qilganda tripni boshqa haydovchilarga qayta yuborish uchun.
-- cancel_count: haydovchilar bekor qilish soni (3 ga yetganda trip yakuniy CANCELLED).
-- excluded_driver_ids: bekor qilgan haydovchilar IDlari (vergul bilan) — bu haydovchilar
--   shu tripni qayta ko'rmaydi va qabul qila olmaydi. notified_driver_ids dan farqli o'laroq
--   (u har matching da qayta yoziladi), bu ro'yxat qayta yuborishlar davomida saqlanadi.
ALTER TABLE trips ADD COLUMN IF NOT EXISTS cancel_count INT NOT NULL DEFAULT 0;
ALTER TABLE trips ADD COLUMN IF NOT EXISTS excluded_driver_ids TEXT;

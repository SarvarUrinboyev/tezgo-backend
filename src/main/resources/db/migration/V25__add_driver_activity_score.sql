-- Haydovchi aktivlik balli (scoring/matching uchun).
-- Trip COMPLETED: +1.0; "BOSHQA" sababli bekor qilish: -0.2; decline / boshqa sabablar: o'zgarmaydi.
-- Yuqori chegara yo'q, manfiy bo'lishi mumkin.
ALTER TABLE drivers ADD COLUMN IF NOT EXISTS activity_score DOUBLE PRECISION NOT NULL DEFAULT 0;

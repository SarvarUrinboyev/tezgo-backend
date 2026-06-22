-- Operatorlar boshqaruvi (Feature A) — staff foydalanuvchilar uchun oxirgi kirish vaqti (audit).
-- Username/parol login muvaffaqiyatli bo'lganda yangilanadi. IDEMPOTENT.
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMP;

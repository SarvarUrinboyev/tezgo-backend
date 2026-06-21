-- Push notification token — Redis yo'q bo'lganda DB fallback uchun
ALTER TABLE drivers ADD COLUMN IF NOT EXISTS push_token VARCHAR(500);

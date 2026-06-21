ALTER TABLE users ADD COLUMN IF NOT EXISTS username VARCHAR(50);
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255);

-- NULL qiymatlar UNIQUE cheklashdan ozod (haydovchilar/yo'lovchilar uchun)
CREATE UNIQUE INDEX IF NOT EXISTS users_username_unique ON users(username);

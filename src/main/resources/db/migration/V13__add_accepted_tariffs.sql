-- Haydovchi qabul qiluvchi tariflar (vergul bilan ajratilgan: EKONOM,DAMAS,BIZNES)
ALTER TABLE drivers ADD COLUMN IF NOT EXISTS accepted_tariffs TEXT DEFAULT 'EKONOM,DAMAS,BIZNES';
UPDATE drivers SET accepted_tariffs = 'EKONOM,DAMAS,BIZNES' WHERE accepted_tariffs IS NULL;

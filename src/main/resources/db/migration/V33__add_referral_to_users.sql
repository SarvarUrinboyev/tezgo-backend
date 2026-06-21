-- Referal (do'st chaqirish) + yo'lovchi hamyoni (bonus balans).
-- balance — tiyin (bonus yig'iladi; online to'lov ulanganda sarflanadi).
-- IDEMPOTENT — xavfsiz migratsiya patterni (jar almashtirishdan oldin qo'lda ham qo'llanadi).
ALTER TABLE users ADD COLUMN IF NOT EXISTS balance BIGINT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS referral_code VARCHAR(12);
ALTER TABLE users ADD COLUMN IF NOT EXISTS referred_by_code VARCHAR(12);
ALTER TABLE users ADD COLUMN IF NOT EXISTS referral_rewarded BOOLEAN NOT NULL DEFAULT FALSE;

-- Referal kod noyob (NULL lar bundan mustasno)
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_referral_code ON users (referral_code) WHERE referral_code IS NOT NULL;

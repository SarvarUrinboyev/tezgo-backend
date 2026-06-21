-- A2: Admin per-driver tariff grants — mashina-modeli defaultidan TASHQARI qo'lda berilgan
-- tariflar (vergul bilan, masalan 'KOMFORT,BIZNES'). Effektiv eligibility = car-default ∪ grants.
ALTER TABLE drivers ADD COLUMN IF NOT EXISTS tariff_grants TEXT;

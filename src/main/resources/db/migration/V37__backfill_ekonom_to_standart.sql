-- Deploy 3 (A2) — tarif nomlarini kanonik qilish.
-- DB tarif qatori EKONOM -> STANDART deb nomlangan edi (admin tahriri), lekin haydovchilarning
-- accepted_tariffs ustuni hali 'EKONOM' tutib turardi -> ular STANDART buyurtmalarni QABUL QILA OLMASDI
-- (DriverTariffFilter.accepts ichida 'EKONOM' != 'STANDART').

-- 1) Mavjud haydovchilar: accepted_tariffs ichidagi EKONOM -> STANDART (case-insensitive)
UPDATE drivers
SET accepted_tariffs = REPLACE(UPPER(accepted_tariffs), 'EKONOM', 'STANDART')
WHERE UPPER(accepted_tariffs) LIKE '%EKONOM%';

-- 2) Yangi haydovchilar uchun ustun DEFAULT qiymatini ham kanonik qilamiz
--    (V13 dagi default 'EKONOM,DAMAS,BIZNES' edi; V13 faylini o'zgartirib bo'lmaydi -> checksum buziladi).
ALTER TABLE drivers ALTER COLUMN accepted_tariffs SET DEFAULT 'STANDART,DAMAS,BIZNES';

-- OPERATOR roli uchun — users jadvalidagi role CHECK ni yangilash
-- PostgreSQL da enum CHECK constraint yo'q bo'lsa, String sifatida saqlanadi
-- JPA @Enumerated(EnumType.STRING) ishlatilgani uchun qo'shimcha o'zgartirish kerak emas

-- Trip.source maydoni allaqachon mavjud (V6 da qo'shilgan, default 'APP')
-- CALL qiymatini qo'llab-quvvatlash uchun CHECK yangilash (agar mavjud bo'lsa)
DO $$
BEGIN
    -- source ustuni mavjudligini tekshirish
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'trips' AND column_name = 'source') THEN
        -- Mavjud CHECK ni o'chirish (agar bor bo'lsa)
        ALTER TABLE trips DROP CONSTRAINT IF EXISTS trips_source_check;
        -- Yangi CHECK — APP va CALL qabul qiladi
        ALTER TABLE trips ADD CONSTRAINT trips_source_check
            CHECK (source IN ('APP', 'CALL', 'ADMIN'));
    END IF;
END $$;

-- Operator uchun index — source=CALL bo'lgan triplarni tez topish
CREATE INDEX IF NOT EXISTS idx_trips_source ON trips(source);

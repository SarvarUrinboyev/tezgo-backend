-- Eski test admin (+998000000000) ni bazadan o'chirish
-- Barcha foreign key bog'lanishlarni avval tozalash kerak

-- broadcast_messages jadvalidagi sent_by ni null qilish.
-- IZOH: sent_by ustuni V6 da qo'shiladi — bu skript V6 dan OLDIN ishlaydi, shuning uchun
-- noldan (V1→V27) migratsiyada ustun hali mavjud emas. Ustun bor bo'lsagina yangilaymiz
-- (prod-da tarixan mavjud edi; toza bazada esa ustun bo'sh — nullash shart emas).
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'broadcast_messages' AND column_name = 'sent_by'
    ) THEN
        UPDATE broadcast_messages SET sent_by = NULL
        WHERE sent_by = (SELECT id FROM users WHERE phone = '+998000000000');
    END IF;
END $$;

-- trips jadvalidagi passenger_id ni null qilish
UPDATE trips SET passenger_id = NULL WHERE passenger_id = (SELECT id FROM users WHERE phone = '+998000000000');

-- driver_photos jadvalidagi reviewed_by ni null qilish
UPDATE driver_photos SET reviewed_by = NULL WHERE reviewed_by = (SELECT id FROM users WHERE phone = '+998000000000');

-- ratings jadvalidagi from_user_id ni null qilish
UPDATE ratings SET from_user_id = NULL WHERE from_user_id = (SELECT id FROM users WHERE phone = '+998000000000');

-- otp_codes jadvalidan o'chirish
DELETE FROM otp_codes WHERE phone = '+998000000000';

-- driver_services jadvalidan o'chirish (agar driver bo'lsa)
DELETE FROM driver_services WHERE driver_id IN (SELECT id FROM drivers WHERE user_id = (SELECT id FROM users WHERE phone = '+998000000000'));

-- drivers jadvalidan o'chirish (agar driver bo'lsa)
DELETE FROM drivers WHERE user_id = (SELECT id FROM users WHERE phone = '+998000000000');

-- users jadvalidan o'chirish
DELETE FROM users WHERE phone = '+998000000000';

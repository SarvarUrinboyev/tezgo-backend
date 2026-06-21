-- Driver support chat — support_messages jadvalini HAYDOVCHI threadlari uchun kengaytirish.
-- Mavjud yo'lovchi/operator support ma'lumotlariga TEGMAYDI (default 'PASSENGER').
-- IDEMPOTENT — jar almashtirishdan oldin qo'lda ham xavfsiz qo'llanadi (tezyol roli ostida).
--
-- thread_type: PASSENGER (mavjud, default) | DRIVER (yangi). Thread egasining turini ajratadi
-- (driver va passenger user_id lari boshqa-boshqa, lekin operator inboxda turini bilish kerak).
-- sender_role da CHECK constraint YO'Q (oddiy VARCHAR(20)) — 'DRIVER' qiymati to'g'ridan-to'g'ri sig'adi.

ALTER TABLE support_messages
    ADD COLUMN IF NOT EXISTS thread_type VARCHAR(10) NOT NULL DEFAULT 'PASSENGER';

-- Operator inbox: o'qilmagan HAYDOVCHI xabarlari uchun partial index
-- (mavjud passenger partial indeksiga parallel — idx_support_messages_operator_unread).
CREATE INDEX IF NOT EXISTS idx_support_messages_driver_unread
    ON support_messages (user_id)
    WHERE read_by_operator = FALSE AND sender_role = 'DRIVER';

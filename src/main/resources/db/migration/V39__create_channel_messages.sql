-- Per-kanal, per-haydovchi xabarlar (admin -> driver, faqat O'QISH uchun 5 ta kanal).
-- Texnik yordam (TEXNIK_YORDAM) BU JADVALDA EMAS — u support_messages (2 tomonlama) da qoladi.
-- Eski broadcast_messages flat feed (📢) endi driver UI da ishlatilmaydi (megaphone bug) — lekin o'chirilmaydi.
-- IDEMPOTENT — jar almashtirishdan oldin qo'lda ham xavfsiz.

CREATE TABLE IF NOT EXISTS channel_messages (
    id          BIGSERIAL    PRIMARY KEY,
    driver_id   BIGINT       NOT NULL REFERENCES drivers(id) ON DELETE CASCADE,
    channel     VARCHAR(32)  NOT NULL,   -- PRO_YANGILIKLARI | BONUSLAR | QOLLAB_QUVVATLASH | OGOHLANTIRISHLAR | HISOB_BALANS
    title       VARCHAR(200),
    body        TEXT         NOT NULL,
    sent_by     BIGINT       REFERENCES users(id),
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    read_at     TIMESTAMP                -- NULL = o'qilmagan (badge shu bo'yicha)
);

-- Bitta haydovchining bitta kanalini vaqt tartibida o'qish
CREATE INDEX IF NOT EXISTS idx_channel_messages_driver_channel
    ON channel_messages (driver_id, channel, created_at);

-- Badge: o'qilmagan xabarlar (partial index)
CREATE INDEX IF NOT EXISTS idx_channel_messages_unread
    ON channel_messages (driver_id, channel)
    WHERE read_at IS NULL;

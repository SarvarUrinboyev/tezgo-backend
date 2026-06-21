-- Support (qo'llab-quvvatlash) chat xabarlari — yo'lovchi <-> operator/admin.
-- Trip chat 24 soatlik Redis'da; BU jadval DOIMIY (Postgres) saqlaydi (suhbat tarixi muhim).
-- IDEMPOTENT — jar almashtirishdan oldin qo'lda ham xavfsiz qo'llanadi (tezyol roli ostida).
CREATE TABLE IF NOT EXISTS support_messages (
    id                 BIGSERIAL PRIMARY KEY,
    user_id            BIGINT       NOT NULL REFERENCES users(id),  -- thread egasi (yo'lovchi)
    sender_role        VARCHAR(20)  NOT NULL,                       -- PASSENGER | OPERATOR
    sender_id          BIGINT,                                      -- aslida yozgan account (audit)
    text               TEXT         NOT NULL,
    read_by_passenger  BOOLEAN      NOT NULL DEFAULT FALSE,
    read_by_operator   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMP    NOT NULL DEFAULT now()
);

-- Bitta yo'lovchi thread'ini vaqt tartibida o'qish uchun
CREATE INDEX IF NOT EXISTS idx_support_messages_user_created
    ON support_messages (user_id, created_at);

-- Operator inbox: o'qilmagan yo'lovchi xabarlari (partial index)
CREATE INDEX IF NOT EXISTS idx_support_messages_operator_unread
    ON support_messages (user_id)
    WHERE read_by_operator = FALSE AND sender_role = 'PASSENGER';

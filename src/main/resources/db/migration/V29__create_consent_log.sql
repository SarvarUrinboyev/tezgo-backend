-- Shaxsiy ma'lumotlarga rozilik yozuvi (UZ "Shaxsga doir ma'lumotlar" qonuni, Buyruq No.3478 maydonlari).
-- Har bir foydalanuvchi kirishda bergan aniq rozilikning isbotlanadigan, vaqt belgili yozuvi.
CREATE TABLE IF NOT EXISTS consent_log (
    id                          BIGSERIAL PRIMARY KEY,
    phone                       VARCHAR(20)  NOT NULL,
    role                        VARCHAR(20),
    consent_type                VARCHAR(40)  NOT NULL DEFAULT 'LOGIN',  -- LOGIN | DRIVER_DOCS
    policy_version              VARCHAR(40)  NOT NULL,
    purposes                    TEXT,
    data_categories             TEXT,
    third_party_allowed         BOOLEAN      DEFAULT TRUE,
    cross_border_allowed        BOOLEAN      DEFAULT FALSE,
    public_distribution_allowed BOOLEAN      DEFAULT FALSE,
    validity_term               VARCHAR(255),
    operator_name               VARCHAR(255),
    operator_tin                VARCHAR(40),
    ip_address                  VARCHAR(64),
    app_version                 VARCHAR(40),
    created_at                  TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_consent_log_phone ON consent_log(phone);
-- Bir foydalanuvchi + tur + siyosat versiyasi uchun bitta yozuv (qayta kirishda takrorlanmaydi)
CREATE UNIQUE INDEX IF NOT EXISTS uq_consent_phone_type_version
    ON consent_log(phone, consent_type, policy_version);

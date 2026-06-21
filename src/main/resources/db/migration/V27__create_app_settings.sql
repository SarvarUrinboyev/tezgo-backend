-- Tizim sozlamalari — key-value (masalan: surge_enabled).
-- Surge default OFF: faqat admin yoqsa talab narxi (multiplier > 1.0) qo'llanadi.
CREATE TABLE app_settings (
    setting_key   VARCHAR(64) PRIMARY KEY,
    setting_value VARCHAR(255) NOT NULL,
    updated_at    TIMESTAMP NOT NULL DEFAULT NOW()
);

INSERT INTO app_settings (setting_key, setting_value) VALUES ('surge_enabled', 'false');

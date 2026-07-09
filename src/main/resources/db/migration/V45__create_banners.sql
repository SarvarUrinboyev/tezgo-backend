CREATE TABLE banners (
    id          BIGSERIAL PRIMARY KEY,
    title       VARCHAR(200) NOT NULL,
    subtitle    VARCHAR(300),
    bg_color    VARCHAR(9) NOT NULL,
    image_url   TEXT,
    link_url    TEXT,
    sort        INTEGER NOT NULL DEFAULT 0,
    active      BOOLEAN NOT NULL DEFAULT true,
    starts_at   TIMESTAMP,
    ends_at     TIMESTAMP,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_banners_active_sort ON banners(active, sort);

INSERT INTO banners (title, subtitle, bg_color, link_url, sort, active)
VALUES ('Barakat SuperMarket', 'Juma aksiyasi — 20% chegirma', '#1D9E75', NULL, 0, true);

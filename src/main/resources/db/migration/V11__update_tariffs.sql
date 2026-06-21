-- EKONOM: boshlang'ich narx 7500 so'm, km uchun 2500 so'm
UPDATE tariffs SET base_price = 750000, price_per_km = 250000, price_per_min = 0, min_price = 0, is_active = true WHERE name = 'EKONOM';

-- KOMFORT: o'chirildi
UPDATE tariffs SET is_active = false WHERE name = 'KOMFORT';

-- BIZNES: boshlang'ich 10000 so'm, km uchun 3000 so'm
UPDATE tariffs SET base_price = 1000000, price_per_km = 300000, price_per_min = 0, min_price = 0, is_active = true WHERE name = 'BIZNES';

-- DAMAS: yangi tarif (agar mavjud bo'lmasa)
INSERT INTO tariffs (name, base_price, price_per_km, price_per_min, min_price, is_active)
SELECT 'DAMAS', 1000000, 250000, 0, 0, true
WHERE NOT EXISTS (SELECT 1 FROM tariffs WHERE name = 'DAMAS');

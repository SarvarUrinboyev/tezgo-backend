-- Fare-bidding (inDrive uslubi): yo'lovchi o'z narxini taklif qiladi.
-- offered_fare NULL bo'lsa — odatdagi metered narx ishlatiladi.
ALTER TABLE trips ADD COLUMN IF NOT EXISTS offered_fare BIGINT;

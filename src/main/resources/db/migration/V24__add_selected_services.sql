-- Operator (yoki kelajakda boshqa manba) buyurtmasida tanlangan qo'shimcha xizmatlar.
-- ServiceType kodlari vergul bilan (masalan "REAR_LUGGAGE,AC"). Narxi extra_price (tiyin) da.
ALTER TABLE trips ADD COLUMN IF NOT EXISTS selected_services TEXT;

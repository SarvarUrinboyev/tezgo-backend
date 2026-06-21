-- ============================================================
-- V3: Tez-tez ishlatiladigan so'rovlar uchun indexlar
-- Performance optimization
-- ============================================================

-- Users: telefon raqam bo'yicha qidirish (login, findByPhone)
CREATE INDEX IF NOT EXISTS idx_users_phone ON users(phone);

-- Drivers: user_id bo'yicha qidirish (findByUserId — har bir so'rovda)
CREATE INDEX IF NOT EXISTS idx_drivers_user_id ON drivers(user_id);

-- Drivers: passport tekshirish (registration duplicate check)
CREATE INDEX IF NOT EXISTS idx_drivers_passport ON drivers(passport_series, passport_number);

-- Drivers: tech passport tekshirish
CREATE INDEX IF NOT EXISTS idx_drivers_tech_passport ON drivers(tech_passport_number);

-- Drivers: location-based queries (matching engine)
CREATE INDEX IF NOT EXISTS idx_drivers_location ON drivers(latitude, longitude) WHERE is_online = true;

-- OTP: expires_at cleanup queries
CREATE INDEX IF NOT EXISTS idx_otp_expires ON otp_codes(expires_at);

-- Ratings: driver rating calculation
CREATE INDEX IF NOT EXISTS idx_ratings_driver ON ratings(to_driver_id);

-- Transactions: created_at for history queries
CREATE INDEX IF NOT EXISTS idx_transactions_created ON transactions(created_at DESC);

-- Driver photos: driver lookup
CREATE INDEX IF NOT EXISTS idx_photos_driver ON driver_photos(driver_id);

-- Trips: composite index for active trip lookups
CREATE INDEX IF NOT EXISTS idx_trips_passenger_status ON trips(passenger_id, status);
CREATE INDEX IF NOT EXISTS idx_trips_driver_status ON trips(driver_id, status);

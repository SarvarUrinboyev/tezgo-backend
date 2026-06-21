-- ============================================================
-- TezYol Taxi — Initial Schema
-- Barcha jadvallar Hibernate entity'lardan olingan
-- ============================================================

-- Users
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    phone VARCHAR(20) UNIQUE NOT NULL,
    name VARCHAR(100),
    role VARCHAR(20) DEFAULT 'PASSENGER',
    avatar_url VARCHAR(500),
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Drivers
CREATE TABLE IF NOT EXISTS drivers (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id),
    car_model VARCHAR(100),
    car_number VARCHAR(20),
    car_color VARCHAR(50),
    car_year INTEGER,
    status VARCHAR(20) DEFAULT 'PENDING',
    is_online BOOLEAN DEFAULT false,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    rating NUMERIC(3,2) DEFAULT 5.0,
    total_trips INTEGER DEFAULT 0,
    balance BIGINT DEFAULT 0,
    passport_series VARCHAR(4),
    passport_number VARCHAR(10) UNIQUE,
    birth_date VARCHAR(15),
    address VARCHAR(200),
    tech_passport_number VARCHAR(20) UNIQUE,
    verified_at TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Tariffs
CREATE TABLE IF NOT EXISTS tariffs (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    base_price BIGINT NOT NULL,
    price_per_km BIGINT NOT NULL,
    price_per_min BIGINT DEFAULT 0,
    min_price BIGINT NOT NULL,
    is_active BOOLEAN DEFAULT true
);

-- Trips
CREATE TABLE IF NOT EXISTS trips (
    id BIGSERIAL PRIMARY KEY,
    passenger_id BIGINT REFERENCES users(id),
    driver_id BIGINT REFERENCES drivers(id),
    tariff_id BIGINT REFERENCES tariffs(id),
    from_lat DOUBLE PRECISION,
    from_lon DOUBLE PRECISION,
    from_address VARCHAR(300),
    to_lat DOUBLE PRECISION,
    to_lon DOUBLE PRECISION,
    to_address VARCHAR(300),
    distance_km DOUBLE PRECISION,
    duration_min INTEGER,
    base_price BIGINT,
    extra_price BIGINT DEFAULT 0,
    total_price BIGINT,
    status VARCHAR(30) DEFAULT 'SEARCHING',
    cancel_reason VARCHAR(300),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    accepted_at TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP
);

-- Transactions
CREATE TABLE IF NOT EXISTS transactions (
    id BIGSERIAL PRIMARY KEY,
    driver_id BIGINT REFERENCES drivers(id),
    trip_id BIGINT REFERENCES trips(id),
    type VARCHAR(30) NOT NULL,
    amount BIGINT NOT NULL,
    balance_before BIGINT,
    balance_after BIGINT,
    description VARCHAR(300),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Ratings
CREATE TABLE IF NOT EXISTS ratings (
    id BIGSERIAL PRIMARY KEY,
    trip_id BIGINT UNIQUE REFERENCES trips(id),
    from_user_id BIGINT REFERENCES users(id),
    to_driver_id BIGINT REFERENCES drivers(id),
    score INTEGER NOT NULL CHECK (score >= 1 AND score <= 5),
    comment VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Driver Photos
CREATE TABLE IF NOT EXISTS driver_photos (
    id BIGSERIAL PRIMARY KEY,
    driver_id BIGINT REFERENCES drivers(id),
    photo_type VARCHAR(50),
    photo_url VARCHAR(500),
    status VARCHAR(20) DEFAULT 'PENDING',
    reject_reason VARCHAR(300),
    uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP,
    reviewed_by BIGINT REFERENCES users(id)
);

-- Driver Services
CREATE TABLE IF NOT EXISTS driver_services (
    id BIGSERIAL PRIMARY KEY,
    driver_id BIGINT REFERENCES drivers(id),
    service_type VARCHAR(30),
    is_enabled BOOLEAN DEFAULT false,
    extra_price BIGINT DEFAULT 0
);

-- Promo Codes
CREATE TABLE IF NOT EXISTS promo_codes (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(30) UNIQUE NOT NULL,
    discount_percent INTEGER NOT NULL CHECK (discount_percent >= 1 AND discount_percent <= 100),
    max_uses INTEGER DEFAULT 100,
    used_count INTEGER DEFAULT 0,
    min_price BIGINT DEFAULT 0,
    valid_from TIMESTAMP,
    valid_to TIMESTAMP,
    is_active BOOLEAN DEFAULT true,
    description VARCHAR(200)
);

-- OTP Codes
CREATE TABLE IF NOT EXISTS otp_codes (
    id BIGSERIAL PRIMARY KEY,
    phone VARCHAR(20) NOT NULL,
    code VARCHAR(10) NOT NULL,
    is_used BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL
);

-- Broadcast Messages
CREATE TABLE IF NOT EXISTS broadcast_messages (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200),
    content TEXT,
    sent_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_trips_passenger ON trips(passenger_id);
CREATE INDEX IF NOT EXISTS idx_trips_driver ON trips(driver_id);
CREATE INDEX IF NOT EXISTS idx_trips_status ON trips(status);
CREATE INDEX IF NOT EXISTS idx_trips_created ON trips(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_transactions_driver ON transactions(driver_id);
CREATE INDEX IF NOT EXISTS idx_transactions_type ON transactions(type);
CREATE INDEX IF NOT EXISTS idx_drivers_status ON drivers(status);
CREATE INDEX IF NOT EXISTS idx_drivers_online ON drivers(is_online);
CREATE INDEX IF NOT EXISTS idx_otp_phone ON otp_codes(phone, is_used);

CREATE TABLE trip_driver_offers (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT,
    trip_id BIGINT NOT NULL REFERENCES trips(id),
    driver_id BIGINT NOT NULL REFERENCES drivers(id),
    generation INTEGER NOT NULL,
    candidate_rank INTEGER NOT NULL,
    distance_km DOUBLE PRECISION,
    status VARCHAR(32) NOT NULL,
    offered_at TIMESTAMP NOT NULL,
    -- Compatibility mirror of response_expires_at. It becomes non-null when the first delivery attempt starts.
    expires_at TIMESTAMP,
    first_delivery_attempt_at TIMESTAMP,
    provider_accepted_at TIMESTAMP,
    response_expires_at TIMESTAMP,
    acknowledged_at TIMESTAMP,
    closed_at TIMESTAMP,
    delivery_attempt_state VARCHAR(32) NOT NULL,
    delivery_error VARCHAR(500),
    delivery_attempt_count INTEGER NOT NULL DEFAULT 0,
    next_delivery_attempt_at TIMESTAMP,
    last_delivery_outcome VARCHAR(40),
    delivery_recipient_fingerprint VARCHAR(64),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT ck_trip_driver_offers_generation_positive CHECK (generation > 0),
    CONSTRAINT ck_trip_driver_offers_rank_non_negative CHECK (candidate_rank >= 0),
    CONSTRAINT uq_trip_driver_offers_trip_generation UNIQUE (trip_id, generation),
    CONSTRAINT uq_trip_driver_offers_trip_driver UNIQUE (trip_id, driver_id)
);

-- The database, rather than a JVM-local check, owns the no-fan-out invariant.
CREATE UNIQUE INDEX uq_trip_driver_offers_one_live_per_trip
    ON trip_driver_offers (trip_id)
    WHERE status IN ('PENDING_DELIVERY', 'ACTIVE', 'ACKNOWLEDGED');

CREATE INDEX idx_trip_driver_offers_driver_live
    ON trip_driver_offers (driver_id, status, response_expires_at);
CREATE INDEX idx_trip_driver_offers_expiry_live
    ON trip_driver_offers (response_expires_at)
    WHERE status IN ('PENDING_DELIVERY', 'ACTIVE', 'ACKNOWLEDGED');

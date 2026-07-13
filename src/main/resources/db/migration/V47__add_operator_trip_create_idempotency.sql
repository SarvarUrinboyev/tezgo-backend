-- Durable HTTP idempotency for operator-created immediate trips. The request
-- hash is stored, never the passenger phone/address payload.
CREATE TABLE operator_trip_idempotencies (
    id              BIGSERIAL PRIMARY KEY,
    operator_id     BIGINT       NOT NULL REFERENCES users(id),
    idempotency_key VARCHAR(36)  NOT NULL,
    request_hash    CHAR(64)     NOT NULL,
    state           VARCHAR(20)  NOT NULL DEFAULT 'PROCESSING',
    trip_id         BIGINT       REFERENCES trips(id),
    response_status VARCHAR(40),
    response_source VARCHAR(20),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_operator_trip_idempotency UNIQUE (operator_id, idempotency_key),
    CONSTRAINT chk_operator_trip_idempotency_state
        CHECK (state IN ('PROCESSING', 'COMPLETED'))
);

CREATE INDEX idx_operator_trip_idempotencies_created_at
    ON operator_trip_idempotencies(created_at);

-- Product invariant already used by the passenger flow: one caller has at
-- most one immediate active trip. This database constraint is the authoritative
-- cross-operator race guard; scheduled trips remain explicitly permitted.
CREATE UNIQUE INDEX uq_trips_one_active_immediate_trip_per_passenger
    ON trips(passenger_id)
    WHERE passenger_id IS NOT NULL
      AND scheduled_at IS NULL
      AND status IN ('SEARCHING', 'ACCEPTED', 'DRIVER_ARRIVED', 'STARTED');

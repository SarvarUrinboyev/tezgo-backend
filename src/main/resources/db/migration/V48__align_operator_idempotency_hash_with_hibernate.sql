-- Hibernate validates PostgreSQL CHAR as bpchar rather than its expected VARCHAR type.
-- SHA-256 is still exactly 64 hexadecimal characters; trim only removes CHAR padding.
ALTER TABLE operator_trip_idempotencies
    ALTER COLUMN request_hash TYPE VARCHAR(64)
    USING BTRIM(request_hash);

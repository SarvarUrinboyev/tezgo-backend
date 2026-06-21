-- CALL_TAXOMETER source qo'shish — operator taxometr rejimida buyurtma yaratadi
ALTER TABLE trips DROP CONSTRAINT IF EXISTS trips_source_check;
ALTER TABLE trips ADD CONSTRAINT trips_source_check
    CHECK (source IN ('APP', 'CALL', 'ADMIN', 'TAXOMETER', 'CALL_TAXOMETER'));

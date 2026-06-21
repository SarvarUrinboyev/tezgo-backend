-- trips_source_check ga TAXOMETER qo'shish
-- V8 da faqat ('APP', 'CALL', 'ADMIN') bor edi
ALTER TABLE trips DROP CONSTRAINT IF EXISTS trips_source_check;
ALTER TABLE trips ADD CONSTRAINT trips_source_check
    CHECK (source IN ('APP', 'CALL', 'ADMIN', 'TAXOMETER'));

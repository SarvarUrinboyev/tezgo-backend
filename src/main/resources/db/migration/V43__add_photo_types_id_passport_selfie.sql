-- ============================================================
-- V43: photo_type CHECK constraintga 4 ta yangi qiymat qo'shish
--      ID_FRONT, ID_BACK, PASSPORT, SELFIE
--
-- Sabab: PhotoType enum'i (PhotoType.java:24-29) A6 va Deploy 3
-- bosqichlarida kengaytirilgan, ammo DB CHECK constraint
-- (V4__fix_photo_type_constraint.sql) yangilanmagan. Natijada:
-- selfie/passport yuklash → DB INSERT chala qoladi
-- ("violates check constraint driver_photos_photo_type_check").
--
-- IDEMPOTENT: DROP IF EXISTS + ADD. Hot-fix uchun prod'ga to'g'ridan-to'g'ri
-- qo'llanildi (Flyway shu o'sha SQL'ni keyingi deploy'da qayta ishga
-- tushirsa ham — natija identik, xato yo'q).
-- ============================================================

ALTER TABLE driver_photos DROP CONSTRAINT IF EXISTS driver_photos_photo_type_check;

ALTER TABLE driver_photos ADD CONSTRAINT driver_photos_photo_type_check
    CHECK (photo_type IN (
        -- V4 dan: avtomobil tomonlari va eski hujjat turlari
        'LEFT_SIDE', 'RIGHT_SIDE', 'FRONT_SIDE', 'REAR_SIDE',
        'FRONT_SEATS', 'REAR_SEATS', 'LICENSE_PLATE', 'DRIVERS_LICENSE',
        'DRIVER_FACE', 'DRIVER_LICENSE_FRONT', 'DRIVER_LICENSE_BACK',
        'CAR_FRONT', 'CAR_SIDE', 'CAR_INTERIOR', 'TECH_PASSPORT',
        -- V43 dan: A6 + Deploy 3 PhotoType enum qo'shimchalari
        'ID_FRONT', 'ID_BACK', 'PASSPORT', 'SELFIE'
    ));

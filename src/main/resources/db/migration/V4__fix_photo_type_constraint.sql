-- ============================================================
-- V4: photo_type CHECK constraintni yangilash
-- Yangi rasm turlari: DRIVER_FACE, CAR_FRONT, CAR_SIDE, etc.
-- ============================================================

-- Eski constraintni o'chirish
ALTER TABLE driver_photos DROP CONSTRAINT IF EXISTS driver_photos_photo_type_check;

-- Yangi constraint — barcha PhotoType enum qiymatlari
ALTER TABLE driver_photos ADD CONSTRAINT driver_photos_photo_type_check
    CHECK (photo_type IN (
        'LEFT_SIDE', 'RIGHT_SIDE', 'FRONT_SIDE', 'REAR_SIDE',
        'FRONT_SEATS', 'REAR_SEATS', 'LICENSE_PLATE', 'DRIVERS_LICENSE',
        'DRIVER_FACE', 'DRIVER_LICENSE_FRONT', 'DRIVER_LICENSE_BACK',
        'CAR_FRONT', 'CAR_SIDE', 'CAR_INTERIOR', 'TECH_PASSPORT'
    ));

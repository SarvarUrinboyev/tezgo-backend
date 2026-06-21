-- Migrate existing photo URLs from static /uploads/ path to protected /api/photos/view/ endpoint
-- Old format: /uploads/drivers/{driverId}/{filename}
-- New format: /api/photos/view/{driverId}/{filename}
UPDATE driver_photos
SET photo_url = REPLACE(photo_url, '/uploads/drivers/', '/api/photos/view/')
WHERE photo_url LIKE '/uploads/drivers/%';

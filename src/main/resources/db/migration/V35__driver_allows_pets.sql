-- V35: haydovchi uy hayvonlarini tashishga rozilik bayrog'i
ALTER TABLE drivers ADD COLUMN IF NOT EXISTS allows_pets BOOLEAN NOT NULL DEFAULT FALSE;

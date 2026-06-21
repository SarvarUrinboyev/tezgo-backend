package com.taxi.backend.enums;

public enum PhotoType {
    // Avtomobil tomonlari (eski nomlar)
    LEFT_SIDE,
    RIGHT_SIDE,
    FRONT_SIDE,
    REAR_SIDE,
    FRONT_SEATS,
    REAR_SEATS,
    LICENSE_PLATE,
    DRIVERS_LICENSE,

    // Yangi nomlar (mobile app ishlatadi)
    DRIVER_FACE,
    DRIVER_LICENSE_FRONT,
    DRIVER_LICENSE_BACK,
    CAR_FRONT,
    CAR_SIDE,
    CAR_INTERIOR,
    TECH_PASSPORT,

    // A6 — Haydovchi hujjat rasmlari: ID karta old/orqa, passport (bitta varaq)
    ID_FRONT,
    ID_BACK,
    PASSPORT,

    // Deploy 3 (A1) — selfie (shaxsni tasdiqlash); UI: 2 element = Passport + Selfie
    SELFIE
}

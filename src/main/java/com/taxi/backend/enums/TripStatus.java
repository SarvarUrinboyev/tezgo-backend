package com.taxi.backend.enums;

import java.util.List;

public enum TripStatus {
    SCHEDULED, // Rejalashtirilgan (keyingi vaqtga) — vaqti kelguncha dispatch qilinmaydi
    SEARCHING, // Haydovchi qidirilmoqda
    ACCEPTED, // Haydovchi qabul qildi
    DRIVER_ARRIVED, // Haydovchi keldi
    STARTED, // Sayohat boshlandi
    COMPLETED, // Yakunlandi
    CANCELLED_BY_PASSENGER, // Yo'lovchi bekor qildi
    CANCELLED_BY_DRIVER, // Haydovchi bekor qildi
    CANCELLED_BY_ADMIN; // Admin bekor qildi

    /**
     * Haydovchi "band" hisoblanadigan statuslar — bu statusdagi tripi bor haydovchi
     * yangi buyurtma olmaydi (matching/broadcast/claim/accept gate'larida tekshiriladi).
     */
    public static final List<TripStatus> ACTIVE_DRIVER_STATUSES = List.of(ACCEPTED, DRIVER_ARRIVED, STARTED);

    /**
     * Yakuniy (terminal) statuslar — bu holatdagi tripni qayta bekor qilib/o'zgartirib bo'lmaydi.
     * Operator bekor qilishi mumkin bo'lgan = bu ro'yxatda BO'LMAGAN har qanday status.
     */
    public static final List<TripStatus> TERMINAL_STATUSES =
            List.of(COMPLETED, CANCELLED_BY_PASSENGER, CANCELLED_BY_DRIVER, CANCELLED_BY_ADMIN);
}

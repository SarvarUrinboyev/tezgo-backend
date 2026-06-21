package com.taxi.backend.enums;

public enum TransactionType {
    TRIP_INCOME, // Sayohatdan daromad
    COMMISSION,  // Komissiya (chegirma)
    TOPUP,       // Balans to'ldirish
    WITHDRAWAL,  // Pul yechish so'rovi
    DEDUCT,      // Balansdan ushlab qolish (admin)
    BONUS,       // Bonus
    PENALTY,     // Jarimalar
    TAXOMETER_COMMISSION // Taxometr komissiya
}

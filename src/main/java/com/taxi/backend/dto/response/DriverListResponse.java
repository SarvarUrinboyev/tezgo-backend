package com.taxi.backend.dto.response;

import java.math.BigDecimal;

public record DriverListResponse(
    Long id, String name, String phone, String driverCode, String carModel, String carNumber,
    String status, boolean isOnline, BigDecimal rating, Integer totalTrips, long balance,
    // ── To'liq ma'lumot (admin "Batafsil" ko'rinishi uchun) ──
    String carColor, Integer carYear, String passportSeries, String passportNumber,
    String birthDate, String address, String techPassportNumber, String acceptedTariffs,
    String verifiedAt, double activityScore, Double latitude, Double longitude,
    String createdAt, String avatarUrl
) {}

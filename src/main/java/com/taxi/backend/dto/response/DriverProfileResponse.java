package com.taxi.backend.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record DriverProfileResponse(
    Long id, Long driverId, String name, String phone, String driverCode,
    String carModel, String carNumber, String carColor, Integer carYear,
    String status, boolean isOnline, BigDecimal rating,
    Integer totalTrips, Long balance, String techPassportNumber,
    long completedTrips, long cancelledTrips, long acceptRate, long cancelRate,
    String acceptedTariffs,
    // Mashina modeli FIZIK qaysi tariflarni bera oladi (DriverTariffFilter natijasi).
    // Frontend: shu to'plamdagi tariflar tanlanadi, qolganlari "qulflangan" ko'rinadi.
    List<String> eligibleTariffs
) {}

package com.taxi.backend.dto.response;

import java.math.BigDecimal;

public record OnlineDriverResponse(
    Long id, Double lat, Double lon, String carModel, String carNumber,
    BigDecimal rating, String carColor, String status, Integer totalTrips,
    String name, String phone, String driverCode, String avatarUrl
) {}

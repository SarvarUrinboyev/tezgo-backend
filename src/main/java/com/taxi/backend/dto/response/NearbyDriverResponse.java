package com.taxi.backend.dto.response;

import java.math.BigDecimal;

public record NearbyDriverResponse(
    Long driverId, double lat, double lon, double distanceKm,
    int etaMinutes, BigDecimal rating, String carModel
) {}

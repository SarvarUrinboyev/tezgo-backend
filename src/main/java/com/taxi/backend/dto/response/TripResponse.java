package com.taxi.backend.dto.response;

import java.math.BigDecimal;

public record TripResponse(
    Long id, String fromAddress, String toAddress,
    Double fromLat, Double fromLon, Double toLat, Double toLon,
    Double distanceKm, Integer durationMin,
    String status, long totalPrice, long basePrice, long extraPrice, long waitingPrice,
    String cancelReason, String source,
    String createdAt, String acceptedAt, String startedAt, String completedAt,
    Long passengerId, String passengerName, String passengerPhone,
    Long driverId, String driverName, String driverPhone,
    String carModel, String carNumber, BigDecimal driverRating,
    String tariffName, Double surgeMultiplier
) {}

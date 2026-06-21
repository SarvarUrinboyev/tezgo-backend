package com.taxi.backend.dto.response;

public record RatingResponse(
    Long id, int score, String comment, String createdAt,
    String passengerName, String driverName, Long tripId
) {}

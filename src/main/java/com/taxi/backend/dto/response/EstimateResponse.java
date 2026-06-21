package com.taxi.backend.dto.response;

public record EstimateResponse(
    long basePrice, long price, long minPrice, double surgeMultiplier,
    String tariffName
) {}

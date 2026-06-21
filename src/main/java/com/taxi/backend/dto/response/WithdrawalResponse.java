package com.taxi.backend.dto.response;

public record WithdrawalResponse(
    Long id, long amount, String description, String createdAt,
    String driverName, String driverPhone
) {}

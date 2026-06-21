package com.taxi.backend.dto.response;

public record PassengerListResponse(
    Long id, String name, String phone, boolean isActive, String role,
    long totalTrips, long totalSpent
) {}

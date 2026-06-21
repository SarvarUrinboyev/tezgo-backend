package com.taxi.backend.dto.response;

public record AuthResponse(
    String token, String refreshToken, Long userId, String phone,
    String role, String name, boolean isRegistered,
    Long driverId, String status, Long balance
) {}

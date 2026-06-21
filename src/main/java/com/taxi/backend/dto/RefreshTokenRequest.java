package com.taxi.backend.dto;

import jakarta.validation.constraints.NotBlank;

/** Token yangilash */
public class RefreshTokenRequest {
    @NotBlank(message = "Refresh token bo'sh bo'lishi mumkin emas")
    private String refreshToken;

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
}

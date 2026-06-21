package com.taxi.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Push notification token saqlash */
public class PushTokenRequest {
    @NotBlank(message = "Token bo'sh bo'lishi mumkin emas")
    @Size(max = 500, message = "Token 500 belgidan oshmasin")
    private String token;

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
}

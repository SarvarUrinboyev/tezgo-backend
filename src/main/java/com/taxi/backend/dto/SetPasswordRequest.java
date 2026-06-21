package com.taxi.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SetPasswordRequest {

    @NotBlank(message = "Yangi parol kiritilmagan")
    @Size(min = 8, max = 128, message = "Parol kamida 8 belgi bo'lishi kerak")
    private String newPassword;

    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
}

package com.taxi.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ChangePasswordRequest {

    @NotBlank(message = "Joriy parol kiritilmagan")
    private String currentPassword;

    @NotBlank(message = "Yangi parol kiritilmagan")
    @Size(min = 8, max = 128, message = "Yangi parol kamida 8 belgi bo'lishi kerak")
    private String newPassword;

    public String getCurrentPassword() { return currentPassword; }
    public void setCurrentPassword(String currentPassword) { this.currentPassword = currentPassword; }

    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
}

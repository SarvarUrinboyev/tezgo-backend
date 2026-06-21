package com.taxi.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class SendOtpRequest {
    @NotBlank(message = "Telefon raqam kerak")
    @Pattern(regexp = "^\\+998\\d{9}$", message = "Telefon formati: +998XXXXXXXXX")
    private String phone;

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}

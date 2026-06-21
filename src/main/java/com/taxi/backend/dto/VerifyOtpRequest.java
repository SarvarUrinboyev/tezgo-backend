package com.taxi.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class VerifyOtpRequest {
    @NotBlank(message = "Telefon raqam kerak")
    private String phone;

    @NotBlank(message = "OTP kod kerak")
    @Size(min = 6, max = 6, message = "OTP 6 xonali bo'lishi kerak")
    private String code;

    private String role = "PASSENGER";

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}

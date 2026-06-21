package com.taxi.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Admin tomonidan operator yaratish */
public class CreateOperatorRequest {
    @NotBlank(message = "Ism bo'sh bo'lishi mumkin emas")
    @Size(min = 2, max = 100)
    private String name;

    @NotBlank(message = "Telefon raqam bo'sh bo'lishi mumkin emas")
    @Pattern(regexp = "^\\+998\\d{9}$", message = "Telefon formati: +998XXXXXXXXX")
    private String phone;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}

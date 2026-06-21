package com.taxi.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Ism o'zgartirish */
public class UpdateNameRequest {
    @NotBlank(message = "Ism bo'sh bo'lishi mumkin emas")
    @Size(min = 2, max = 100, message = "Ism 2-100 belgi orasida bo'lishi kerak")
    private String name;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}

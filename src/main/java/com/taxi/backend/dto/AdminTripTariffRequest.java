package com.taxi.backend.dto;

import jakarta.validation.constraints.NotNull;

/** Admin — buyurtma tarifini almashtirish (faqat SEARCHING statusida). */
public class AdminTripTariffRequest {
    @NotNull(message = "tariffId kerak")
    private Long tariffId;

    public Long getTariffId() { return tariffId; }
    public void setTariffId(Long tariffId) { this.tariffId = tariffId; }
}

package com.taxi.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Balans to'ldirish uchun payment order */
public class TopupRequest {
    @NotNull(message = "Summa kiritilishi shart")
    @Min(value = 100, message = "Minimal summa 100 tiyin")
    private Long amount;

    public Long getAmount() { return amount; }
    public void setAmount(Long amount) { this.amount = amount; }
}

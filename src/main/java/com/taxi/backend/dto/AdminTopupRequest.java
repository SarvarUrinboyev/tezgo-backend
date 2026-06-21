package com.taxi.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public class AdminTopupRequest {

    @NotNull(message = "amount (UZS) kerak")
    @Min(value = 1, message = "Miqdor musbat bo'lishi kerak")
    private Long amount;

    @NotBlank(message = "paymentMethod kerak: CASH yoki CARD")
    @Pattern(regexp = "CASH|CARD", message = "paymentMethod faqat 'CASH' yoki 'CARD' bo'lishi mumkin")
    private String paymentMethod;

    public Long getAmount() { return amount; }
    public void setAmount(Long amount) { this.amount = amount; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
}

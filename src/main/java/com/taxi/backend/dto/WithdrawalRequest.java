package com.taxi.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class WithdrawalRequest {
    @NotNull(message = "Miqdor kerak")
    @Min(value = 1, message = "Miqdor musbat bo'lishi kerak")
    private Long amount;

    private String method = "CARD";
    private String account = "";

    public Long getAmount() { return amount; }
    public void setAmount(Long amount) { this.amount = amount; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getAccount() { return account; }
    public void setAccount(String account) { this.account = account; }
}

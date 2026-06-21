package com.taxi.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Rad etish sababi — photo reject, withdrawal reject uchun */
public class RejectRequest {
    @NotBlank(message = "Sabab bo'sh bo'lishi mumkin emas")
    @Size(max = 500, message = "Sabab 500 belgidan oshmasin")
    private String reason;

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}

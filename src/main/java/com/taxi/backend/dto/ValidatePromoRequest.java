package com.taxi.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Promo kodni tekshirish */
public class ValidatePromoRequest {
    @NotBlank(message = "Promo kod bo'sh bo'lishi mumkin emas")
    @Size(max = 50, message = "Promo kod 50 belgidan oshmasin")
    private String code;

    @Min(value = 0, message = "Narx manfiy bo'lishi mumkin emas")
    private Long tripPrice;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public Long getTripPrice() { return tripPrice; }
    public void setTripPrice(Long tripPrice) { this.tripPrice = tripPrice; }
}

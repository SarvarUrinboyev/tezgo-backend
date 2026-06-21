package com.taxi.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class UpdateServicePriceRequest {
    @NotNull(message = "Narx ko'rsatilishi shart")
    @Min(value = 0, message = "Narx manfiy bo'lmasligi kerak")
    private Long price;

    public Long getPrice() { return price; }
    public void setPrice(Long price) { this.price = price; }
}

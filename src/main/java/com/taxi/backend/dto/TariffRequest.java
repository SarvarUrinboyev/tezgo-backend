package com.taxi.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class TariffRequest {
    @NotBlank(message = "Tarif nomi bo'sh bo'lmasligi kerak")
    @Size(max = 50, message = "Tarif nomi juda uzun")
    private String name;

    @NotNull(message = "Bazaviy narx ko'rsatilishi shart")
    @Min(value = 0, message = "Narx manfiy bo'lmasligi kerak")
    private Long basePrice;

    @NotNull(message = "Km narx ko'rsatilishi shart")
    @Min(value = 0, message = "Narx manfiy bo'lmasligi kerak")
    private Long pricePerKm;

    @Min(value = 0, message = "Narx manfiy bo'lmasligi kerak")
    private Long pricePerMin;

    @Min(value = 0, message = "Narx manfiy bo'lmasligi kerak")
    private Long minPrice;

    private boolean active = true;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getBasePrice() { return basePrice; }
    public void setBasePrice(Long basePrice) { this.basePrice = basePrice; }
    public Long getPricePerKm() { return pricePerKm; }
    public void setPricePerKm(Long pricePerKm) { this.pricePerKm = pricePerKm; }
    public Long getPricePerMin() { return pricePerMin; }
    public void setPricePerMin(Long pricePerMin) { this.pricePerMin = pricePerMin; }
    public Long getMinPrice() { return minPrice; }
    public void setMinPrice(Long minPrice) { this.minPrice = minPrice; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}

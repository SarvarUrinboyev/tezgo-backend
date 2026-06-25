package com.taxi.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Admin — buyurtma A (olib ketish) va B (manzil) ma'lumotlarini tahrirlash. Faqat SEARCHING statusida. */
public class AdminTripAddressesRequest {
    @NotBlank(message = "fromAddress kerak")
    private String fromAddress;

    @NotNull(message = "fromLat kerak")
    private Double fromLat;

    @NotNull(message = "fromLon kerak")
    private Double fromLon;

    private String toAddress;
    private Double toLat;
    private Double toLon;

    public String getFromAddress() { return fromAddress; }
    public void setFromAddress(String fromAddress) { this.fromAddress = fromAddress; }

    public Double getFromLat() { return fromLat; }
    public void setFromLat(Double fromLat) { this.fromLat = fromLat; }

    public Double getFromLon() { return fromLon; }
    public void setFromLon(Double fromLon) { this.fromLon = fromLon; }

    public String getToAddress() { return toAddress; }
    public void setToAddress(String toAddress) { this.toAddress = toAddress; }

    public Double getToLat() { return toLat; }
    public void setToLat(Double toLat) { this.toLat = toLat; }

    public Double getToLon() { return toLon; }
    public void setToLon(Double toLon) { this.toLon = toLon; }
}

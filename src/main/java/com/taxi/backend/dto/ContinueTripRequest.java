package com.taxi.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class ContinueTripRequest {
    @NotNull(message = "Kenglik ko'rsatilishi shart")
    private Double toLat;

    @NotNull(message = "Uzunlik ko'rsatilishi shart")
    private Double toLon;

    private String toAddress;

    @NotNull(message = "Masofa ko'rsatilishi shart")
    @Positive(message = "Masofa musbat bo'lishi kerak")
    private Double distanceKm;

    public Double getToLat() { return toLat; }
    public void setToLat(Double toLat) { this.toLat = toLat; }
    public Double getToLon() { return toLon; }
    public void setToLon(Double toLon) { this.toLon = toLon; }
    public String getToAddress() { return toAddress; }
    public void setToAddress(String toAddress) { this.toAddress = toAddress; }
    public Double getDistanceKm() { return distanceKm; }
    public void setDistanceKm(Double distanceKm) { this.distanceKm = distanceKm; }
}

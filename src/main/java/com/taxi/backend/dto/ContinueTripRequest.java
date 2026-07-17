package com.taxi.backend.dto;

import jakarta.validation.constraints.NotNull;

public class ContinueTripRequest {
    @NotNull(message = "Kenglik ko'rsatilishi shart")
    private Double toLat;

    @NotNull(message = "Uzunlik ko'rsatilishi shart")
    private Double toLon;

    private String toAddress;

    /** Deprecated client estimate; server derives the billable distance. */
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

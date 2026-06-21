package com.taxi.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class EstimateRequest {
    @NotNull(message = "Tarif ko'rsatilishi shart")
    private Long tariffId;

    @NotNull(message = "Masofa ko'rsatilishi shart")
    @Positive(message = "Masofa musbat bo'lishi kerak")
    private Double distanceKm;

    private Double lat;
    private Double lon;

    public Long getTariffId() { return tariffId; }
    public void setTariffId(Long tariffId) { this.tariffId = tariffId; }
    public Double getDistanceKm() { return distanceKm; }
    public void setDistanceKm(Double distanceKm) { this.distanceKm = distanceKm; }
    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }
    public Double getLon() { return lon; }
    public void setLon(Double lon) { this.lon = lon; }
}

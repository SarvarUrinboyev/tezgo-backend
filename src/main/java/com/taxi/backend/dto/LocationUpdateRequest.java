package com.taxi.backend.dto;

import jakarta.validation.constraints.NotNull;

public class LocationUpdateRequest {
    @NotNull(message = "Kenglik (lat) kerak")
    private Double lat;

    @NotNull(message = "Uzunlik (lon) kerak")
    private Double lon;

    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }
    public Double getLon() { return lon; }
    public void setLon(Double lon) { this.lon = lon; }
}

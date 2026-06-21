package com.taxi.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class PlaceRequest {

    @NotBlank(message = "Joy nomi bo'sh bo'lmasligi kerak")
    @Size(max = 200, message = "Joy nomi 200 ta belgidan oshmasligi kerak")
    private String name;

    @NotNull(message = "Kenglik (lat) ko'rsatilishi shart")
    private Double lat;

    @NotNull(message = "Uzunlik (lon) ko'rsatilishi shart")
    private Double lon;

    private String aliases;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }

    public Double getLon() { return lon; }
    public void setLon(Double lon) { this.lon = lon; }

    public String getAliases() { return aliases; }
    public void setAliases(String aliases) { this.aliases = aliases; }
}

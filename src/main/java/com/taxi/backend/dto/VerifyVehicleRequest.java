package com.taxi.backend.dto;

import jakarta.validation.constraints.NotBlank;

public class VerifyVehicleRequest {
    @NotBlank(message = "Texnik pasport raqami kerak")
    private String techPassport;

    @NotBlank(message = "Davlat raqami kerak")
    private String plateNumber;

    public String getTechPassport() { return techPassport; }
    public void setTechPassport(String techPassport) { this.techPassport = techPassport; }
    public String getPlateNumber() { return plateNumber; }
    public void setPlateNumber(String plateNumber) { this.plateNumber = plateNumber; }
}

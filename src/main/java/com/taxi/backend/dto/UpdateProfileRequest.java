package com.taxi.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public class UpdateProfileRequest {
    @Size(max = 100, message = "Model nomi juda uzun")
    private String carModel;

    @Size(max = 20, message = "Mashina raqami juda uzun")
    private String carNumber;

    @Size(max = 50, message = "Rang nomi juda uzun")
    private String carColor;

    @Min(value = 1990, message = "Yil noto'g'ri")
    @Max(value = 2030, message = "Yil noto'g'ri")
    private Integer carYear;

    @Size(max = 100, message = "Ism juda uzun")
    private String name;

    public String getCarModel() { return carModel; }
    public void setCarModel(String carModel) { this.carModel = carModel; }
    public String getCarNumber() { return carNumber; }
    public void setCarNumber(String carNumber) { this.carNumber = carNumber; }
    public String getCarColor() { return carColor; }
    public void setCarColor(String carColor) { this.carColor = carColor; }
    public Integer getCarYear() { return carYear; }
    public void setCarYear(Integer carYear) { this.carYear = carYear; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}

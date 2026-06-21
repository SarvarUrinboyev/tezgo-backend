package com.taxi.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public class RegisterDriverRequest {
    private String phone;

    @NotBlank(message = "Ism kiritilmagan")
    private String name;

    private String birthDate;
    private String address;
    private String passportSeries;
    private String passportNumber;

    @NotBlank(message = "Avtomobil modeli kerak")
    private String carModel;

    @NotBlank(message = "Davlat raqami kerak")
    private String carNumber;

    private String carColor;

    @Min(value = 1975, message = "Avtomobil 50 yoshdan oshmasligi kerak")
    @Max(value = 2030, message = "Yil 2030 dan katta bo'lmasligi kerak")
    private Integer carYear;

    private String techPassportNumber;

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getBirthDate() { return birthDate; }
    public void setBirthDate(String birthDate) { this.birthDate = birthDate; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getPassportSeries() { return passportSeries; }
    public void setPassportSeries(String passportSeries) { this.passportSeries = passportSeries; }
    public String getPassportNumber() { return passportNumber; }
    public void setPassportNumber(String passportNumber) { this.passportNumber = passportNumber; }
    public String getCarModel() { return carModel; }
    public void setCarModel(String carModel) { this.carModel = carModel; }
    public String getCarNumber() { return carNumber; }
    public void setCarNumber(String carNumber) { this.carNumber = carNumber; }
    public String getCarColor() { return carColor; }
    public void setCarColor(String carColor) { this.carColor = carColor; }
    public Integer getCarYear() { return carYear; }
    public void setCarYear(Integer carYear) { this.carYear = carYear; }
    public String getTechPassportNumber() { return techPassportNumber; }
    public void setTechPassportNumber(String techPassportNumber) { this.techPassportNumber = techPassportNumber; }
}

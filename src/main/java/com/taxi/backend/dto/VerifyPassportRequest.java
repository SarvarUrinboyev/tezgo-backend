package com.taxi.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class VerifyPassportRequest {
    @NotBlank(message = "Pasport seriyasi kerak")
    @Size(min = 2, max = 2, message = "Seriya 2 ta harf")
    @Pattern(regexp = "^[A-Za-z]{2}$", message = "Seriya faqat 2 ta harfdan iborat bo'lishi kerak")
    private String series;

    @NotBlank(message = "Pasport raqami kerak")
    @Size(min = 7, max = 7, message = "Raqam 7 xonali")
    @Pattern(regexp = "^\\d{7}$", message = "Raqam faqat 7 ta raqamdan iborat bo'lishi kerak")
    private String number;

    @NotBlank(message = "Tug'ilgan sana kerak")
    @Pattern(regexp = "^\\d{2}\\.\\d{2}\\.\\d{4}$", message = "Sana formati: KK.OO.YYYY")
    private String birthDate;

    @Size(max = 20, message = "Captcha juda uzun")
    private String captcha;

    @Size(max = 100, message = "Session ID juda uzun")
    private String sessionId;

    public String getSeries() { return series; }
    public void setSeries(String series) { this.series = series; }
    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }
    public String getBirthDate() { return birthDate; }
    public void setBirthDate(String birthDate) { this.birthDate = birthDate; }
    public String getCaptcha() { return captcha; }
    public void setCaptcha(String captcha) { this.captcha = captcha; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
}

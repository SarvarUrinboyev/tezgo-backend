package com.taxi.backend.dto;

import jakarta.validation.constraints.*;

public class BookTripRequest {
    @NotNull(message = "Tarif tanlang")
    private Long tariffId;

    @NotBlank(message = "Qayerdan manzil kerak")
    @Size(max = 300, message = "Manzil 300 belgidan oshmasligi kerak")
    private String fromAddress;

    @Size(max = 300, message = "Manzil 300 belgidan oshmasligi kerak")
    private String toAddress;

    @DecimalMin(value = "0.0", message = "Masofa manfiy bo'lishi mumkin emas")
    @DecimalMax(value = "500.0", message = "Masofa 500 km dan oshmasligi kerak")
    private Double distance;

    /** FIXED (default) yoki TAXOMETER */
    private String tripMode;

    @NotNull(message = "Ketish koordinatasi kerak")
    @DecimalMin(value = "-90.0", message = "Latitude -90 dan kichik bo'lishi mumkin emas")
    @DecimalMax(value = "90.0", message = "Latitude 90 dan katta bo'lishi mumkin emas")
    private Double fromLat;

    @NotNull(message = "Ketish koordinatasi kerak")
    @DecimalMin(value = "-180.0", message = "Longitude -180 dan kichik bo'lishi mumkin emas")
    @DecimalMax(value = "180.0", message = "Longitude 180 dan katta bo'lishi mumkin emas")
    private Double fromLon;

    @DecimalMin(value = "-90.0", message = "Latitude -90 dan kichik bo'lishi mumkin emas")
    @DecimalMax(value = "90.0", message = "Latitude 90 dan katta bo'lishi mumkin emas")
    private Double toLat;

    @DecimalMin(value = "-180.0", message = "Longitude -180 dan kichik bo'lishi mumkin emas")
    @DecimalMax(value = "180.0", message = "Longitude 180 dan katta bo'lishi mumkin emas")
    private Double toLon;

    @Size(max = 30, message = "Promo kod 30 belgidan oshmasligi kerak")
    @Pattern(regexp = "^[A-Za-z0-9_-]*$", message = "Promo kodda faqat harf va raqam")
    private String promoCode;

    // Fare-bidding: yo'lovchi taklif qilgan narx (tiyin). NULL = metered narx.
    @jakarta.validation.constraints.Min(value = 0, message = "Taklif narxi manfiy bo'lmaydi")
    @jakarta.validation.constraints.Max(value = 100000000L, message = "Taklif narxi juda katta")
    private Long offeredFare;

    public Long getOfferedFare() { return offeredFare; }
    public void setOfferedFare(Long offeredFare) { this.offeredFare = offeredFare; }

    public Long getTariffId() { return tariffId; }
    public void setTariffId(Long tariffId) { this.tariffId = tariffId; }
    public String getFromAddress() { return fromAddress; }
    public void setFromAddress(String fromAddress) { this.fromAddress = fromAddress; }
    public String getToAddress() { return toAddress; }
    public void setToAddress(String toAddress) { this.toAddress = toAddress; }
    public Double getDistance() { return distance; }
    public void setDistance(Double distance) { this.distance = distance; }
    public Double getFromLat() { return fromLat; }
    public void setFromLat(Double fromLat) { this.fromLat = fromLat; }
    public Double getFromLon() { return fromLon; }
    public void setFromLon(Double fromLon) { this.fromLon = fromLon; }
    public Double getToLat() { return toLat; }
    public void setToLat(Double toLat) { this.toLat = toLat; }
    public Double getToLon() { return toLon; }
    public void setToLon(Double toLon) { this.toLon = toLon; }
    public String getPromoCode() { return promoCode; }
    public void setPromoCode(String promoCode) { this.promoCode = promoCode; }
    public String getTripMode() { return tripMode; }
    public void setTripMode(String tripMode) { this.tripMode = tripMode; }

    /** Rejalashtirilgan buyurtma vaqti — epoch millis (UTC). NULL/0 = darhol buyurtma. */
    private Long scheduledAt;
    public Long getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(Long scheduledAt) { this.scheduledAt = scheduledAt; }
}

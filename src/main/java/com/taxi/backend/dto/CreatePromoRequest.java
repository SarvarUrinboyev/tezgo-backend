package com.taxi.backend.dto;

import jakarta.validation.constraints.*;

/** Yangi promo kod yaratish */
public class CreatePromoRequest {
    @NotBlank(message = "Kod bo'sh bo'lishi mumkin emas")
    @Size(min = 3, max = 30, message = "Kod 3-30 belgi orasida")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Kod faqat harf, raqam, - va _ dan iborat")
    private String code;

    @NotNull(message = "Chegirma foizi kiritilishi shart")
    @Min(value = 1, message = "Minimal chegirma 1%")
    @Max(value = 100, message = "Maksimal chegirma 100%")
    private Integer discountPercent;

    @NotNull @Min(value = 1, message = "Minimal foydalanish soni 1")
    private Integer maxUses;

    @Min(value = 0)
    private Long minPrice = 0L;

    @Size(max = 200) private String description;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public Integer getDiscountPercent() { return discountPercent; }
    public void setDiscountPercent(Integer discountPercent) { this.discountPercent = discountPercent; }
    public Integer getMaxUses() { return maxUses; }
    public void setMaxUses(Integer maxUses) { this.maxUses = maxUses; }
    public Long getMinPrice() { return minPrice; }
    public void setMinPrice(Long minPrice) { this.minPrice = minPrice; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}

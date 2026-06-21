package com.taxi.backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Promo kod.
 * Misollar: TEZYOL20 → 20% chegirma, YANGI10 → 10% chegirma
 */
@Entity
@Table(name = "promo_codes")
public class PromoCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code; // masalan: "TEZYOL20"

    @Column(name = "discount_percent", nullable = false)
    private int discountPercent; // 1-100

    @Column(name = "max_uses", nullable = false)
    private int maxUses = 100; // maksimal foydalanish soni

    @Column(name = "used_count", nullable = false)
    private int usedCount = 0;

    @Column(name = "min_price")
    private Long minPrice = 0L; // minimal buyurtma narxi (tiyinlarda)

    @Column(name = "valid_from")
    private LocalDateTime validFrom;

    @Column(name = "valid_to")
    private LocalDateTime validTo;

    @Column(name = "is_active")
    private boolean isActive = true;

    @Column(length = 200)
    private String description;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public PromoCode() {}

    // ─── Getters & Setters ───

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code.toUpperCase().trim(); }

    public int getDiscountPercent() { return discountPercent; }
    public void setDiscountPercent(int discountPercent) { this.discountPercent = discountPercent; }

    public int getMaxUses() { return maxUses; }
    public void setMaxUses(int maxUses) { this.maxUses = maxUses; }

    public int getUsedCount() { return usedCount; }
    public void setUsedCount(int usedCount) { this.usedCount = usedCount; }

    public Long getMinPrice() { return minPrice; }
    public void setMinPrice(Long minPrice) { this.minPrice = minPrice; }

    public LocalDateTime getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDateTime validFrom) { this.validFrom = validFrom; }

    public LocalDateTime getValidTo() { return validTo; }
    public void setValidTo(LocalDateTime validTo) { this.validTo = validTo; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    /** Promo hozir amal qilishini tekshirish */
    public boolean isValid(Long tripPrice) {
        if (!isActive) return false;
        if (usedCount >= maxUses) return false;
        if (minPrice != null && tripPrice < minPrice) return false;
        LocalDateTime now = LocalDateTime.now();
        if (validFrom != null && now.isBefore(validFrom)) return false;
        if (validTo != null && now.isAfter(validTo)) return false;
        return true;
    }

    /** Chegirma miqdorini hisoblash (tiyinlarda) */
    public long calculateDiscount(long price) {
        return (long) (price * discountPercent / 100.0);
    }
}

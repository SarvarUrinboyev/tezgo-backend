package com.taxi.backend.dto.response;

public record PromoCodeResponse(
    Long id, String code, int discountPercent, int maxUses, int usedCount,
    long minPrice, boolean isActive, String description, String createdAt
) {}

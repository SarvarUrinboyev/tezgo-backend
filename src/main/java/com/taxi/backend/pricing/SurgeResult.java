package com.taxi.backend.pricing;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Surge hisoblash natijasi — multiplier, narx va barcha omillar.
 */
public record SurgeResult(
        double multiplier,
        long finalPriceTiyin,
        String level,
        List<SurgeFactor> factors
) {
    /** UI uchun omillar ro'yxati (faqat ta'sir qilganlarini ko'rsatish) */
    public List<Map<String, Object>> activeFactors() {
        return factors.stream()
                .filter(f -> !f.isNeutral())
                .map(f -> Map.<String, Object>of(
                        "name", f.name(),
                        "value", f.value(),
                        "reason", f.reason()))
                .collect(Collectors.toList());
    }
}

package com.taxi.backend.enums;

public enum ServiceType {
    DELIVERY("Dastavka", 500_000L),        // 5 000 so'm
    ROOF_LUGGAGE("Tom Bagaj", 1_400_000L), // 14 000 so'm
    REAR_LUGGAGE("Orqa Bagaj", 500_000L),  // 5 000 so'm
    AC("Konditsioner", 200_000L),          // 2 000 so'm
    CABIN_CARGO("Salonga Yuk", 300_000L);  // 3 000 so'm

    private final String uzName;
    private final long defaultPriceTiyin;

    ServiceType(String uzName, long defaultPriceTiyin) {
        this.uzName = uzName;
        this.defaultPriceTiyin = defaultPriceTiyin;
    }

    /** O'zbekcha nomi (UI uchun) */
    public String getUzName() {
        return uzName;
    }

    /** Standart narx (tiyinlarda) — operator buyurtmasida haydovchi noma'lum bo'lgani uchun */
    public long getDefaultPriceTiyin() {
        return defaultPriceTiyin;
    }
}

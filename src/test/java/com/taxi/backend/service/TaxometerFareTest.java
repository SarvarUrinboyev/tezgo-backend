package com.taxi.backend.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Taxometer narx hisoblash va Haversine formulasini tekshirish.
 * DB yoki Spring context kerak emas — sof mantiq testlari.
 * Tarif: EKONOM — base=750000 tiyin (7500 so'm), perKm=250000 tiyin (2500 so'm/km)
 */
class TaxometerFareTest {

    // ─── Fare calculation (EKONOM tariff values) ──────────────────────────────

    /** fareTiyin = base(750000) + distanceKm * pricePerKm(250000), both in tiyin */
    static long fareTiyin(double distanceKm) {
        return 750_000L + (long) (distanceKm * 250_000L);
    }

    @Test
    void fare_oneKm_returnsBaseAndKm() {
        // 750000 + 1 * 250000 = 1000000 tiyin = 10000 so'm
        assertEquals(1_000_000L, fareTiyin(1.0));
    }

    @Test
    void fare_twoKm_correctTiyin() {
        // 750000 + 2 * 250000 = 1250000 tiyin = 12500 so'm
        assertEquals(1_250_000L, fareTiyin(2.0));
    }

    @Test
    void fare_zeroKm_returnsBaseOnly() {
        assertEquals(750_000L, fareTiyin(0.0));
    }

    @Test
    void fare_tenKm_correctTiyin() {
        // 750000 + 10 * 250000 = 3250000 tiyin = 32500 so'm
        assertEquals(3_250_000L, fareTiyin(10.0));
    }

    // ─── Commission calculation ───────────────────────────────────────────────

    @Test
    void commission_tenPercent_oneKm() {
        // fareTiyin=1000000, 10% = 100000 tiyin = 1000 so'm
        long fare = fareTiyin(1.0);
        long commission = Math.round(fare * 10.0 / 100.0);
        assertEquals(1_000_000L, fare);
        assertEquals(100_000L, commission);
    }

    @Test
    void commission_tenPercent_tenKm() {
        // fareTiyin=3250000, 10% = 325000 tiyin = 3250 so'm
        long fare = fareTiyin(10.0);
        long commission = Math.round(fare * 10.0 / 100.0);
        assertEquals(3_250_000L, fare);
        assertEquals(325_000L, commission);
    }

    // ─── Haversine formula ─────────────────────────────────────────────────────

    /** Haversine formula — taxometer screen bilan bir xil hisoblash */
    static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    @Test
    void haversine_samePoint_returnsZero() {
        double d = haversineKm(41.3111, 69.2797, 41.3111, 69.2797);
        assertEquals(0.0, d, 0.0001);
    }

    @Test
    void haversine_parkentToTashkent_reasonableDistance() {
        // Parkent (~41.30, 69.68) → Toshkent markazi (~41.30, 69.28)
        double d = haversineKm(41.30, 69.68, 41.30, 69.28);
        // ~35 km atrofida bo'lishi kerak
        assertTrue(d > 30 && d < 45, "Parkent-Toshkent masofa 30-45 km oralig'ida: " + d);
    }

    @Test
    void haversine_shortDistance_lessThanOneKm() {
        // ~100m shimolda (0.001 daraja ≈ 111 metr)
        double d = haversineKm(41.3000, 69.3000, 41.3010, 69.3000);
        assertTrue(d < 0.2, "Qisqa masofa 200m dan kam bo'lishi kerak: " + d);
    }

    // ─── Integration: start → accumulate → finish ─────────────────────────────

    @Test
    void fullTrip_accumulateThenFare() {
        // Haydovchi 3 ta segment bo'yicha yuradi
        double totalKm = 0;
        double[][] segments = {
                {41.3000, 69.3000, 41.3050, 69.3000}, // ~556 m
                {41.3050, 69.3000, 41.3100, 69.3050}, // ~622 m
                {41.3100, 69.3050, 41.3150, 69.3100}, // ~621 m
        };
        for (double[] seg : segments) {
            totalKm += haversineKm(seg[0], seg[1], seg[2], seg[3]);
        }

        long fare = fareTiyin(totalKm);
        long fareUzs = fare / 100;
        long commissionTiyin = Math.round(fare * 10.0 / 100.0);

        assertTrue(totalKm > 1.5 && totalKm < 2.5, "Jami masofa 1.5-2.5 km: " + totalKm);
        // base=7500 + ~1.8km*2500=4500 → ~12000 so'm
        assertTrue(fareUzs >= 10_000 && fareUzs <= 16_000, "Narx 10000-16000 so'm: " + fareUzs);
        assertEquals(Math.round(fare * 0.1), commissionTiyin);
    }
}

package com.taxi.backend.pricing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Tungi tarif — avtomatik, hech qanday toggle yo'q.
 *
 * QOIDA:
 *   Biznes-zona (pricing.night.zone, default Asia/Tashkent) bo'yicha 00:00–06:00
 *   oralig'ida YARATILGAN har qanday safarga asosiy/boshlang'ich (base) narxga bir
 *   martalik +3 000 so'm qo'shiladi. Har km narxi O'ZGARMAYDI. Barcha tariflar va
 *   barcha manbalar (operator, passenger app, taxometr).
 *
 * ZONA (kritik):
 *   Tungi oyna JVM/host zonasidan MUSTAQIL — qaror biznes-zonada olinadi.
 *   - "Hozir" yo'llari (estimate, bookTrip, operator createTrip): biznes-zonaga
 *     bog'langan Clock orqali (isNightNow / applyToBaseNow).
 *   - createdAt yo'llari (operator updateTrip, taxometer finish): saqlangan
 *     LocalDateTime (zonasiz 'timestamp', JVM=UTC da yozilgan) UTC instant deb
 *     olinadi va biznes-zonaga aylantiriladi — BITTA umumiy helper (storedToInstant).
 *
 * BIRLIK: ichki narxlar tiyinda (1 so'm = 100 tiyin). Config so'm da → ×100.
 *
 * SURGE bilan tartib: base → +night (additiv, base ga, bir marta) → surge (× night-li
 * base ustiga). Tungi tarif surge toggle'idan mustaqil.
 */
@Service
public class NightFareService {

    private final long surchargeSom;
    private final int startHour;
    private final int endHour;
    private final Clock clock;
    private final ZoneId businessZone;

    public NightFareService(
            @Value("${pricing.night.surcharge:3000}") long surchargeSom,
            @Value("${pricing.night.start:0}") int startHour,
            @Value("${pricing.night.end:6}") int endHour,
            @Qualifier("nightClock") Clock nightClock) {
        this.surchargeSom = surchargeSom;
        this.startHour = startHour;
        this.endHour = endHour;
        this.clock = nightClock;
        this.businessZone = nightClock.getZone();
    }

    // ── Tun qarori (biznes-zona) ────────────────────────────────────────────────

    /** Hozir (biznes-zona) tungi oynadami — "hozir" yo'llari uchun. */
    public boolean isNightNow() {
        return isNightAt(clock.instant());
    }

    /** Berilgan instant biznes-zonada tungi oynadami ([start, end)). */
    public boolean isNightAt(Instant instant) {
        if (instant == null) return false;
        int hour = instant.atZone(businessZone).getHour();
        return hour >= startHour && hour < endHour;
    }

    /**
     * BITTA umumiy konversiya: saqlangan createdAt (zonasiz 'timestamp', JVM=UTC da
     * yozilgan = UTC wall-clock) → Instant. Ad-hoc soat matematikasi yo'q.
     */
    public Instant storedToInstant(LocalDateTime storedUtc) {
        return storedUtc == null ? null : storedUtc.toInstant(ZoneOffset.UTC);
    }

    // ── Ustama ──────────────────────────────────────────────────────────────────

    public long surchargeTiyin() { return surchargeSom * 100L; }

    public long surchargeSom() { return surchargeSom; }

    public ZoneId zone() { return businessZone; }

    /** "Hozir" yo'llari — base ga tun ustamasi (biznes-zona, hozirgi vaqt). */
    public long applyToBaseNow(long baseTiyin) {
        return isNightNow() ? baseTiyin + surchargeTiyin() : baseTiyin;
    }

    /** createdAt yo'llari — saqlangan yaratish vaqti bo'yicha (UTC→biznes-zona). */
    public long applyToBaseAtCreation(long baseTiyin, LocalDateTime createdAt) {
        return isNightAt(storedToInstant(createdAt)) ? baseTiyin + surchargeTiyin() : baseTiyin;
    }
}

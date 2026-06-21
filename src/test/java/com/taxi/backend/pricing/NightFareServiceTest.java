package com.taxi.backend.pricing;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tungi tarif — zona-aware, JVM zonasidan MUSTAQIL.
 *
 * Bu klass davomida JVM default zonasi ataylab NON-Tashkent (America/New_York) ga
 * o'rnatiladi — qarorlar baribir biznes-zonada (Asia/Tashkent) olinishini isbotlash uchun.
 * Konfiguratsiya: +3000 so'm, oyna [0,6). Tariflar (tiyin): Ekonom 750000, DAMAS/Biznes 1000000.
 */
class NightFareServiceTest {

    private static final ZoneId TASHKENT = ZoneId.of("Asia/Tashkent"); // UTC+5, DST yo'q
    private static final long EKONOM_BASE = 750_000L;
    private static final long DAMAS_BASE = 1_000_000L;

    private static TimeZone ORIGINAL_TZ;

    @BeforeAll
    static void forceNonTashkentJvmZone() {
        ORIGINAL_TZ = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
    }

    @AfterAll
    static void restoreJvmZone() {
        TimeZone.setDefault(ORIGINAL_TZ);
    }

    /** Tashkent wall-clock (h:m) ga teng instant. */
    private Instant tashkentInstant(int h, int m) {
        return LocalDateTime.of(2026, 6, 13, h, m).atZone(TASHKENT).toInstant();
    }

    /** Biznes-zona = Tashkent ga bog'langan, berilgan Tashkent vaqtida muzlatilgan Clock. */
    private NightFareService atTashkent(int h, int m) {
        return new NightFareService(3000, 0, 6, Clock.fixed(tashkentInstant(h, m), TASHKENT));
    }

    /** UTC 'timestamp' ustunda saqlangandek LocalDateTime (zonasiz, JVM=UTC da yozilgan). */
    private LocalDateTime storedUtc(int h, int m) {
        return LocalDateTime.of(2026, 6, 13, h, m);
    }

    private NightFareService nf() {
        // createdAt yo'llari uchun clock vaqti ahamiyatsiz — faqat zona Tashkent bo'lsa bas.
        return new NightFareService(3000, 0, 6, Clock.fixed(Instant.EPOCH, TASHKENT));
    }

    // ── isNightNow: Tashkent oyna chegaralari, JVM=NY bo'lsa ham ────────────────

    @Test
    void isNightNow_tashkent_2359_isNotNight() {
        assertFalse(atTashkent(23, 59).isNightNow());
    }

    @Test
    void isNightNow_tashkent_0000_isNight() {
        assertTrue(atTashkent(0, 0).isNightNow(), "00:00 Tashkent tun");
    }

    @Test
    void isNightNow_tashkent_0559_isNight() {
        assertTrue(atTashkent(5, 59).isNightNow());
    }

    @Test
    void isNightNow_tashkent_0600_isNotNight() {
        assertFalse(atTashkent(6, 0).isNightNow(), "06:00 Tashkent tun EMAS");
    }

    @Test
    void isNightNow_tashkent_noon_isNotNight() {
        assertFalse(atTashkent(12, 0).isNightNow());
    }

    // ── Zona-mustaqillik: eski LocalDateTime.now() xatti-harakati ostida YIQILADI ──

    @Test
    void nightDecisionIsZoneIndependent_wouldFailUnderJvmLocalNow() {
        // Tashkent 01:00 = UTC 20:00 = New York 16:00. Tashkent bo'yicha — TUN.
        Instant instant = tashkentInstant(1, 0);
        NightFareService s = new NightFareService(3000, 0, 6, Clock.fixed(instant, TASHKENT));

        assertTrue(s.isNightNow(), "Yangi: Tashkent 01:00 → tun");

        // Eski yo'l (JVM/UTC soatidan o'qish) shu instant uchun TUN EMAS deydi → bu bug edi:
        int jvmHour = instant.atZone(ZoneId.systemDefault()).getHour();   // New York = 16
        int utcHour = instant.atZone(ZoneOffset.UTC).getHour();           // UTC = 20
        assertFalse(jvmHour >= 0 && jvmHour < 6, "JVM(NY) soati tunni o'tkazib yuborardi");
        assertFalse(utcHour >= 0 && utcHour < 6, "UTC soati ham tunni o'tkazib yuborardi");
    }

    // ── Ustama miqdori va birligi ──────────────────────────────────────────────

    @Test
    void surcharge_amountAndUnit() {
        NightFareService s = nf();
        assertEquals(3000L, s.surchargeSom());
        assertEquals(300_000L, s.surchargeTiyin());
    }

    // ── applyToBaseNow: tun → +3000 (bir marta), kunduz → o'zgarmaydi, barcha tariflar ──

    @Test
    void applyToBaseNow_atNight_addsSurchargeOnce_allTariffs() {
        NightFareService night = atTashkent(1, 0);
        assertEquals(1_050_000L, night.applyToBaseNow(EKONOM_BASE), "Ekonom 7500→10500");
        assertEquals(1_300_000L, night.applyToBaseNow(DAMAS_BASE), "DAMAS 10000→13000");
        assertEquals(night.surchargeTiyin(), night.applyToBaseNow(EKONOM_BASE) - EKONOM_BASE);
    }

    @Test
    void applyToBaseNow_atDay_unchanged() {
        assertEquals(EKONOM_BASE, atTashkent(12, 0).applyToBaseNow(EKONOM_BASE));
    }

    // ── createdAt yo'li: saqlangan UTC timestamp → biznes-zona, chegaralar ─────

    @Test
    void applyToBaseAtCreation_storedUtcConvertedToTashkent() {
        NightFareService s = nf();
        // UTC 20:00 = Tashkent 01:00 → tun
        assertEquals(EKONOM_BASE + 300_000L, s.applyToBaseAtCreation(EKONOM_BASE, storedUtc(20, 0)));
        // UTC 08:00 = Tashkent 13:00 → kunduz
        assertEquals(EKONOM_BASE, s.applyToBaseAtCreation(EKONOM_BASE, storedUtc(8, 0)));
    }

    @Test
    void isNightAt_createdAt_boundaries() {
        NightFareService s = nf();
        assertTrue(s.isNightAt(s.storedToInstant(storedUtc(19, 0))), "UTC19:00=Tashkent00:00 tun");
        assertFalse(s.isNightAt(s.storedToInstant(storedUtc(18, 59))), "UTC18:59=Tashkent23:59 emas");
        assertTrue(s.isNightAt(s.storedToInstant(storedUtc(0, 59))), "UTC00:59=Tashkent05:59 tun");
        assertFalse(s.isNightAt(s.storedToInstant(storedUtc(1, 0))), "UTC01:00=Tashkent06:00 emas");
    }

    @Test
    void isNightAt_null_isNotNight() {
        assertFalse(nf().isNightAt(null));
        assertNull(nf().storedToInstant(null));
    }

    // ── Sozlanuvchi oyna ──────────────────────────────────────────────────────

    @Test
    void configurableWindow_respectsStartAndEnd() {
        // 01:00–05:00, +5000, Tashkent
        java.util.function.BiFunction<Integer, Integer, NightFareService> at =
                (h, m) -> new NightFareService(5000, 1, 5,
                        Clock.fixed(tashkentInstant(h, m), TASHKENT));
        assertFalse(at.apply(0, 30).isNightNow());
        assertTrue(at.apply(1, 0).isNightNow());
        assertTrue(at.apply(4, 59).isNightNow());
        assertFalse(at.apply(5, 0).isNightNow());
        assertEquals(500_000L, at.apply(2, 0).surchargeTiyin());
    }
}

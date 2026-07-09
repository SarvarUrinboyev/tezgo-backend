package com.taxi.backend.service;

import com.taxi.backend.model.Tariff;
import com.taxi.backend.model.Trip;
import com.taxi.backend.model.User;
import com.taxi.backend.pricing.NightFareService;
import com.taxi.backend.pricing.SurgeResult;
import com.taxi.backend.repository.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Passenger estimate + bookTrip — tungi tarif ZONA-aware wiring.
 *
 * JVM default zonasi ataylab America/New_York; tungi qaror baribir biznes-zonada
 * (Asia/Tashkent) olinadi — real NightFareService + muzlatilgan Clock orqali.
 * Surge echo (×1.0) qilinadi; order-lock testida surge ×1.5.
 */
@ExtendWith(MockitoExtension.class)
class TripEstimateNightTest {

    private static final ZoneId TASHKENT = ZoneId.of("Asia/Tashkent");
    private static TimeZone ORIGINAL_TZ;

    @BeforeAll static void forceNyZone() {
        ORIGINAL_TZ = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
    }
    @AfterAll static void restoreZone() { TimeZone.setDefault(ORIGINAL_TZ); }

    @Mock private TripRepository tripRepository;
    @Mock private DriverRepository driverRepository;
    @Mock private TariffRepository tariffRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private RatingRepository ratingRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private MatchingService matchingService;
    @Mock private SurgePricingService surgePricingService;
    @Mock private PushNotificationService pushService;
    @Mock private PromoCodeService promoCodeService;
    @Mock private AsyncNotificationService asyncNotifier;
    @Mock private TripNotificationHelper notificationHelper;
    @Mock private SecurityMonitorService securityMonitor;
    @Mock private SmsInviteService smsInviteService;

    private Instant tashkent(int h, int m) {
        return LocalDateTime.of(2026, 6, 13, h, m).atZone(TASHKENT).toInstant();
    }

    /** TripService, tungi clock biznes-zonada berilgan Tashkent vaqtida muzlatilgan. */
    private TripService serviceAt(int h, int m) {
        NightFareService nf = new NightFareService(3000, 0, 6, Clock.fixed(tashkent(h, m), TASHKENT));
        return new TripService(tripRepository, driverRepository, tariffRepository,
                transactionRepository, ratingRepository, messagingTemplate, matchingService,
                surgePricingService, Optional.empty(), pushService, promoCodeService,
                asyncNotifier, notificationHelper, securityMonitor, smsInviteService, nf, org.mockito.Mockito.mock(com.taxi.backend.service.ReferralService.class), org.mockito.Mockito.mock(com.taxi.backend.repository.DriverPhotoRepository.class));
    }

    private Tariff ekonom() {
        Tariff t = new Tariff();
        t.setId(1L); t.setName("EKONOM");
        t.setBasePrice(750_000L); t.setPricePerKm(250_000L); t.setMinPrice(500_000L);
        return t;
    }

    private void echoSurge() {
        when(surgePricingService.calculate(anyLong(), anyDouble(), anyDouble()))
                .thenAnswer(inv -> new SurgeResult(1.0, inv.getArgument(0), "NORMAL", List.of()));
    }

    private long estimateSom(int h, int m) {
        when(tariffRepository.findById(1L)).thenReturn(Optional.of(ekonom()));
        echoSurge();
        return ((Number) serviceAt(h, m).estimate(1L, 5.0, 41.3, 69.6).get("estimatedPrice")).longValue();
    }

    // ── estimate: Tashkent oyna chegaralari (JVM=NY bo'lsa ham) ─────────────────
    // base = 7500 + 5*2500 = 20000 so'm; tunda +3000 = 23000.

    @Test void estimate_tashkent_2359_day() { assertEquals(20_000L, estimateSom(23, 59)); }
    @Test void estimate_tashkent_0000_night() { assertEquals(23_000L, estimateSom(0, 0)); }
    @Test void estimate_tashkent_0559_night() { assertEquals(23_000L, estimateSom(5, 59)); }
    @Test void estimate_tashkent_0600_day() { assertEquals(20_000L, estimateSom(6, 0)); }

    @Test
    void estimate_nightExposesFlags() {
        when(tariffRepository.findById(1L)).thenReturn(Optional.of(ekonom()));
        echoSurge();
        Map<String, Object> r = serviceAt(1, 0).estimate(1L, 5.0, 41.3, 69.6);
        assertEquals(true, r.get("isNight"));
        assertEquals(3000L, ((Number) r.get("nightSurcharge")).longValue());
    }

    // ── ORDER-LOCK: base → +night (additiv) → surge (× night-li base ustiga) ────
    @Test
    void orderIsBaseThenNightThenSurge() {
        when(tariffRepository.findById(1L)).thenReturn(Optional.of(ekonom()));
        // Surge ×1.5 — night-li base ustiga ko'paytirilishi kerak
        when(surgePricingService.calculate(anyLong(), anyDouble(), anyDouble()))
                .thenAnswer(inv -> {
                    long b = inv.getArgument(0);
                    return new SurgeResult(1.5, Math.round(b * 1.5), "HIGH", List.of());
                });
        // base 2,000,000 tiyin → +night 300,000 → 2,300,000 → ×1.5 = 3,450,000 → 34,500 so'm
        long som = ((Number) serviceAt(1, 0).estimate(1L, 5.0, 41.3, 69.6).get("estimatedPrice")).longValue();
        assertEquals(34_500L, som, "surge night-li base (2.3M) ustiga; surge-then-night bo'lsa 33000 chiqardi");
    }

    // ── bookTrip (now-path) tunda +3000 ─────────────────────────────────────────
    private long bookTotalSom(int h, int m) {
        when(tariffRepository.findById(1L)).thenReturn(Optional.of(ekonom()));
        when(tripRepository.findFirstByPassengerIdAndStatusIn(anyLong(), anyList())).thenReturn(Optional.empty());
        when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> inv.getArgument(0));
        echoSurge();
        User p = new User(); p.setId(5L); p.setPhone("+998901112233");
        // from==to → haversine 0 → masofa manipulyatsiya tekshiruvi o'tkazib yuboriladi
        Map<String, Object> r = serviceAt(h, m).bookTrip(p, 1L, 41.3, 69.6, "A",
                41.3, 69.6, "B", 5.0, null, "APP");
        return ((Number) r.get("totalPrice")).longValue() / 100;
    }

    @Test
    void bookTrip_night_addsExactly3000() {
        long day = bookTotalSom(12, 0);
        reset(tariffRepository, tripRepository, surgePricingService);
        long night = bookTotalSom(1, 0);
        assertEquals(day + 3000, night);
    }
}

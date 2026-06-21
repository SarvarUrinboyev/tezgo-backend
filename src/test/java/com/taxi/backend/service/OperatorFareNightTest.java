package com.taxi.backend.service;

import com.taxi.backend.dto.OperatorTripRequest;
import com.taxi.backend.enums.TripStatus;
import com.taxi.backend.model.Tariff;
import com.taxi.backend.model.Trip;
import com.taxi.backend.model.User;
import com.taxi.backend.pricing.NightFareService;
import com.taxi.backend.pricing.SurgeResult;
import com.taxi.backend.repository.TariffRepository;
import com.taxi.backend.repository.TripRepository;
import com.taxi.backend.repository.UserRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Operator createTrip (now-path) + editTrip (createdAt-path) — tungi tarif ZONA-aware.
 * JVM=America/New_York bo'lsa ham qaror Asia/Tashkent da olinadi.
 */
@ExtendWith(MockitoExtension.class)
class OperatorFareNightTest {

    private static final ZoneId TASHKENT = ZoneId.of("Asia/Tashkent");
    private static TimeZone ORIGINAL_TZ;

    @BeforeAll static void forceNyZone() {
        ORIGINAL_TZ = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
    }
    @AfterAll static void restoreZone() { TimeZone.setDefault(ORIGINAL_TZ); }

    @Mock private TripRepository tripRepository;
    @Mock private UserRepository userRepository;
    @Mock private TariffRepository tariffRepository;
    @Mock private SurgePricingService surgePricingService;
    @Mock private TripNotificationHelper notificationHelper;
    @Mock private SecurityMonitorService securityMonitor;

    private Instant tashkent(int h, int m) {
        return LocalDateTime.of(2026, 6, 13, h, m).atZone(TASHKENT).toInstant();
    }

    private OperatorService serviceAt(int h, int m) {
        NightFareService nf = new NightFareService(3000, 0, 6, Clock.fixed(tashkent(h, m), TASHKENT));
        return new OperatorService(tripRepository, userRepository, tariffRepository,
                surgePricingService, notificationHelper, securityMonitor, nf);
    }

    private Tariff ekonom() {
        Tariff t = new Tariff();
        t.setId(1L); t.setName("EKONOM");
        t.setBasePrice(750_000L); t.setPricePerKm(250_000L); t.setMinPrice(500_000L);
        return t;
    }

    private OperatorTripRequest fixedReq() {
        OperatorTripRequest r = new OperatorTripRequest();
        r.setPassengerPhone("+998901112233");
        r.setTariffId(1L);
        r.setPickupAddress("A"); r.setDestinationAddress("B");
        r.setFromLat(41.30); r.setFromLon(69.68);
        r.setToLat(41.31); r.setToLon(69.69);
        return r;
    }

    private long createSom(OperatorService svc) {
        User operator = new User(); operator.setId(99L);
        User passenger = new User(); passenger.setId(2L);
        lenient().when(userRepository.findByPhone(anyString())).thenReturn(Optional.of(passenger));
        lenient().when(tariffRepository.findById(1L)).thenReturn(Optional.of(ekonom()));
        lenient().when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(surgePricingService.calculate(anyLong(), anyDouble(), anyDouble()))
                .thenAnswer(inv -> new SurgeResult(1.0, inv.getArgument(0), "NORMAL", List.of()));
        return ((Number) svc.createTrip(operator, fixedReq()).get("estimatedPrice")).longValue();
    }

    // ── createTrip (now-path): Tashkent oyna chegaralari ───────────────────────
    @Test
    void createTrip_tashkentBoundaries_nightAdds3000() {
        long day = createSom(serviceAt(12, 0));
        assertEquals(day, createSom(serviceAt(23, 59)), "23:59 kunduz");
        assertEquals(day + 3000, createSom(serviceAt(0, 0)), "00:00 tun");
        assertEquals(day + 3000, createSom(serviceAt(5, 59)), "05:59 tun");
        assertEquals(day, createSom(serviceAt(6, 0)), "06:00 kunduz");
    }

    // ── editTrip (createdAt-path): night createdAt vaqti bo'yicha ───────────────
    private long editSom(LocalDateTime storedCreatedAtUtc) {
        // editTrip uchun clock vaqti ahamiyatsiz (createdAt ishlatiladi)
        OperatorService svc = serviceAt(12, 0);
        Trip trip = new Trip();
        trip.setTariff(ekonom());
        trip.setSource("CALL");
        trip.setStatus(TripStatus.SEARCHING);
        trip.setCreatedAt(storedCreatedAtUtc);
        trip.setFromLat(41.30); trip.setFromLon(69.68);
        trip.setToLat(41.31); trip.setToLon(69.69);
        when(tripRepository.findById(7L)).thenReturn(Optional.of(trip));
        when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> inv.getArgument(0));
        when(surgePricingService.calculate(anyLong(), anyDouble(), anyDouble()))
                .thenAnswer(inv -> new SurgeResult(1.0, inv.getArgument(0), "NORMAL", List.of()));
        User operator = new User(); operator.setId(99L);
        return ((Number) svc.editTrip(operator, 7L, fixedReq()).get("estimatedPrice")).longValue();
    }

    @Test
    void editTrip_usesCreationTimeInTashkent() {
        // UTC 08:00 = Tashkent 13:00 → kunduz
        long day = editSom(LocalDateTime.of(2026, 6, 13, 8, 0));
        reset(tripRepository, surgePricingService);
        // UTC 20:00 = Tashkent 01:00 → tun → +3000
        long night = editSom(LocalDateTime.of(2026, 6, 13, 20, 0));
        assertEquals(day + 3000, night);
    }
}

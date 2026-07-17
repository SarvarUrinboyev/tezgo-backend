package com.taxi.backend.service;

import com.taxi.backend.dto.OperatorTripRequest;
import com.taxi.backend.enums.TripStatus;
import com.taxi.backend.model.Driver;
import com.taxi.backend.model.Tariff;
import com.taxi.backend.model.Trip;
import com.taxi.backend.model.User;
import com.taxi.backend.pricing.NightFareService;
import com.taxi.backend.pricing.SurgeResult;
import com.taxi.backend.repository.DriverRepository;
import com.taxi.backend.repository.TariffRepository;
import com.taxi.backend.repository.TransactionRepository;
import com.taxi.backend.repository.TripRepository;
import com.taxi.backend.repository.UserRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
 * Taxometr LIFECYCLE — tungi tarif aniq BIR MARTA qo'llanishini isbotlaydi.
 *
 * Oqim: operator CALL_TAXOMETER booking (startingPrice) → driver finish (yakuniy narx).
 * Tekshiruv:
 *   - booking startingPrice tunda base + 3000 (bir marta).
 *   - finish yakuniy narx = kunduzgi yakuniy + aynan 3000 (bir marta), createdAt bo'yicha.
 *   - finish booking startingPrice'ni JAMLAMAYDI (qayta hisoblaydi) — double-charge yo'q.
 *
 * EKONOM: base 750000 (7500), perKm 250000 (2500). 4 km.
 *   day finish   = 750000 + 4*250000 = 1,750,000 = 17500 so'm
 *   night finish = (750000+300000) + 4*250000 = 2,050,000 = 20500 so'm
 *   night booking startingPrice = 750000+300000 = 1,050,000 = 10500 so'm
 */
@ExtendWith(MockitoExtension.class)
class TaxometerLifecycleNightTest {

    private static final ZoneId TASHKENT = ZoneId.of("Asia/Tashkent");
    private static TimeZone ORIGINAL_TZ;

    @BeforeAll static void forceNyZone() {
        ORIGINAL_TZ = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
    }
    @AfterAll static void restoreZone() { TimeZone.setDefault(ORIGINAL_TZ); }

    @Mock private TripRepository tripRepository;
    @Mock private UserRepository userRepository;
    @Mock private DriverRepository driverRepository;
    @Mock private TariffRepository tariffRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private SurgePricingService surgePricingService;
    @Mock private TripNotificationHelper notificationHelper;
    @Mock private SecurityMonitorService securityMonitor;

    private NightFareService nightAt(int h, int m) {
        Instant i = LocalDateTime.of(2026, 6, 13, h, m).atZone(TASHKENT).toInstant();
        return new NightFareService(3000, 0, 6, Clock.fixed(i, TASHKENT));
    }

    private Tariff ekonom() {
        Tariff t = new Tariff();
        t.setId(1L); t.setName("STANDART");
        t.setBasePrice(750_000L); t.setPricePerKm(250_000L); t.setMinPrice(500_000L);
        return t;
    }

    private long taxometerFinishUzs(NightFareService nf, LocalDateTime createdAtUtc) {
        TaxometerService taxo = new TaxometerService(tripRepository, driverRepository,
                transactionRepository, tariffRepository, nf);
        User user = new User(); user.setId(7L);
        Driver driver = new Driver(); driver.setId(3L); driver.setBalance(0L);
        driver.setLatitude(41.346);
        driver.setLongitude(69.690);
        Trip trip = new Trip();
        trip.setDriver(driver);
        trip.setSource("CALL_TAXOMETER");
        trip.setStatus(TripStatus.STARTED);
        trip.setCreatedAt(createdAtUtc);
        trip.setFromLat(41.310);
        trip.setFromLon(69.690);
        when(driverRepository.findByUserId(7L)).thenReturn(Optional.of(driver));
        when(tripRepository.findById(50L)).thenReturn(Optional.of(trip));
        when(tariffRepository.findByName("STANDART")).thenReturn(Optional.of(ekonom()));
        when(driverRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(driver));
        Map<String, Object> r = taxo.finish(user, 50L, 41.31, 69.69, 400.0);
        return ((Number) r.get("fareUzs")).longValue();
    }

    @Test
    void nightSurchargeAppliedExactlyOnce_acrossBookingAndFinish() {
        // ── 1) Booking (operator CALL_TAXOMETER) at night → startingPrice = base + 3000 once ──
        OperatorService operator = new OperatorService(tripRepository, userRepository, tariffRepository,
                surgePricingService, notificationHelper, securityMonitor, nightAt(1, 0));
        User op = new User(); op.setId(99L);
        User passenger = new User(); passenger.setId(2L);
        when(userRepository.findByPhone(anyString())).thenReturn(Optional.of(passenger));
        when(tariffRepository.findById(1L)).thenReturn(Optional.of(ekonom()));
        ArgumentCaptor<Trip> savedTrip = ArgumentCaptor.forClass(Trip.class);
        when(tripRepository.save(savedTrip.capture())).thenAnswer(inv -> inv.getArgument(0));

        OperatorTripRequest req = new OperatorTripRequest();
        req.setPassengerPhone("+998901112233");
        req.setTariffId(1L);
        req.setMode("TAXOMETER");
        req.setPickupAddress("A");
        req.setFromLat(41.30); req.setFromLon(69.68);

        Map<String, Object> booking = operator.createTrip(op, req);
        long startingPriceSom = ((Number) booking.get("startingPrice")).longValue();
        assertEquals(10_500L, startingPriceSom, "booking: base 7500 + tungi 3000 (bir marta)");
        // Saqlangan trip base'ida ham tun bir marta
        assertEquals(1_050_000L, savedTrip.getValue().getBasePrice());

        // ── 2) Finish at night (createdAt = Tashkent 01:00 = UTC 20:00) ──
        reset(tripRepository, tariffRepository, driverRepository);
        long nightFinish = taxometerFinishUzs(nightAt(1, 0), LocalDateTime.of(2026, 6, 13, 20, 0));

        // ── 3) Day finish baseline (createdAt = Tashkent 13:00 = UTC 08:00) ──
        reset(tripRepository, tariffRepository, driverRepository);
        long dayFinish = taxometerFinishUzs(nightAt(13, 0), LocalDateTime.of(2026, 6, 13, 8, 0));

        assertTrue(dayFinish > 17_500L);
        assertEquals(dayFinish + 3000, nightFinish);
        // Tun aniq BIR MARTA: yakuniy = kunduzgi + 3000
        assertEquals(dayFinish + 3000, nightFinish, "tungi ustama bir marta");
        // finish startingPrice'ni JAMLAMAYDI (double-charge yo'q): 20500 != 10500 + 17500
        assertNotEquals(startingPriceSom + dayFinish, nightFinish,
                "finish booking starting price'ni qo'shmasligi kerak (qayta hisoblaydi)");
    }
}

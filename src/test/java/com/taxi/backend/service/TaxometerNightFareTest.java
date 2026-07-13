package com.taxi.backend.service;

import com.taxi.backend.enums.TripStatus;
import com.taxi.backend.model.Driver;
import com.taxi.backend.model.Tariff;
import com.taxi.backend.model.Trip;
import com.taxi.backend.model.User;
import com.taxi.backend.pricing.NightFareService;
import com.taxi.backend.repository.DriverRepository;
import com.taxi.backend.repository.TariffRepository;
import com.taxi.backend.repository.TransactionRepository;
import com.taxi.backend.repository.TripRepository;
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
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Taxometr finish — tungi tarif ZONA-aware (createdAt yo'li).
 * Night safar YARATILGAN vaqti bo'yicha, biznes-zonada (Asia/Tashkent), JVM zonasidan mustaqil.
 * Per-km va kutish/xizmat haqi o'zgarmaydi; base ga +3000 bir marta.
 */
@ExtendWith(MockitoExtension.class)
class TaxometerNightFareTest {

    private static final ZoneId TASHKENT = ZoneId.of("Asia/Tashkent");
    private static TimeZone ORIGINAL_TZ;

    @BeforeAll static void forceNyZone() {
        ORIGINAL_TZ = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
    }
    @AfterAll static void restoreZone() { TimeZone.setDefault(ORIGINAL_TZ); }

    @Mock private TripRepository tripRepository;
    @Mock private DriverRepository driverRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private TariffRepository tariffRepository;

    private TaxometerService service() {
        NightFareService nf = new NightFareService(3000, 0, 6, Clock.fixed(Instant.EPOCH, TASHKENT));
        return new TaxometerService(tripRepository, driverRepository,
                transactionRepository, tariffRepository, nf);
    }

    private Tariff ekonom() {
        Tariff t = new Tariff();
        t.setName("STANDART");
        t.setBasePrice(750_000L); t.setPricePerKm(250_000L); t.setMinPrice(500_000L);
        return t;
    }

    private long finishFareUzs(LocalDateTime createdAtUtc) {
        User user = new User(); user.setId(7L);
        Driver driver = new Driver(); driver.setId(3L); driver.setBalance(0L);
        Trip trip = new Trip();
        trip.setDriver(driver);
        trip.setSource("TAXOMETER");
        trip.setStatus(TripStatus.STARTED);
        trip.setCreatedAt(createdAtUtc);

        when(driverRepository.findByUserId(7L)).thenReturn(Optional.of(driver));
        when(tripRepository.findById(50L)).thenReturn(Optional.of(trip));
        when(tariffRepository.findByName("STANDART")).thenReturn(Optional.of(ekonom()));
        when(driverRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(driver));

        Map<String, Object> r = service().finish(user, 50L, 41.31, 69.69, 4.0);
        return ((Number) r.get("fareUzs")).longValue();
    }

    @Test
    void finish_nightByCreationTimeInTashkent_adds3000() {
        // UTC 08:00 = Tashkent 13:00 → kunduz: 7500 + 4*2500 = 17500
        long day = finishFareUzs(LocalDateTime.of(2026, 6, 13, 8, 0));
        assertEquals(17_500L, day);

        reset(driverRepository, tripRepository, tariffRepository);
        // UTC 20:00 = Tashkent 01:00 → tun: +3000 = 20500
        long night = finishFareUzs(LocalDateTime.of(2026, 6, 13, 20, 0));
        assertEquals(20_500L, night);
        assertEquals(day + 3000, night);
    }

    @Test
    void finish_tashkentBoundaries() {
        // UTC 19:00 = Tashkent 00:00 → tun
        assertEquals(20_500L, finishFareUzs(LocalDateTime.of(2026, 6, 13, 19, 0)));
        reset(driverRepository, tripRepository, tariffRepository);
        // UTC 18:59 = Tashkent 23:59 → kunduz
        assertEquals(17_500L, finishFareUzs(LocalDateTime.of(2026, 6, 13, 18, 59)));
        reset(driverRepository, tripRepository, tariffRepository);
        // UTC 01:00 = Tashkent 06:00 → kunduz (oyna [0,6))
        assertEquals(17_500L, finishFareUzs(LocalDateTime.of(2026, 6, 13, 1, 0)));
    }
}

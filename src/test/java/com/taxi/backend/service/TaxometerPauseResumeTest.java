package com.taxi.backend.service;

import com.taxi.backend.enums.TripStatus;
import com.taxi.backend.model.Driver;
import com.taxi.backend.model.Trip;
import com.taxi.backend.model.User;
import com.taxi.backend.repository.DriverRepository;
import com.taxi.backend.repository.TariffRepository;
import com.taxi.backend.repository.TransactionRepository;
import com.taxi.backend.repository.TripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** A5 — taxometer PAUZA/DAVOM: kutish davri waitingPrice ga to'g'ri (free-seconds bilan) qo'shiladi. */
@ExtendWith(MockitoExtension.class)
class TaxometerPauseResumeTest {

    @Mock private TripRepository tripRepository;
    @Mock private DriverRepository driverRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private TariffRepository tariffRepository;

    private TaxometerService svc;
    private User user;
    private Driver driver;
    private Trip trip;

    @BeforeEach
    void setup() {
        svc = new TaxometerService(tripRepository, driverRepository, transactionRepository, tariffRepository,
                new com.taxi.backend.pricing.NightFareService(0, 0, 0, java.time.Clock.systemUTC()));
        ReflectionTestUtils.setField(svc, "waitingPricePerMinute", 60_000L);
        ReflectionTestUtils.setField(svc, "waitingFreeSeconds", 60L);

        user = new User(); user.setId(100L);
        driver = new Driver(); driver.setId(5L);
        when(driverRepository.findByUserId(100L)).thenReturn(Optional.of(driver));

        trip = new Trip();
        trip.setId(1L);
        trip.setDriver(driver);
        trip.setSource("TAXOMETER");
        trip.setStatus(TripStatus.STARTED);
        trip.setWaitingPrice(0L);
        when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));
    }

    @Test
    void pause_setsWaitingStarted() {
        Map<String, Object> r = svc.pause(user, 1L);
        assertTrue((Boolean) r.get("paused"));
        assertNotNull(trip.getWaitingStartedAt());
    }

    @Test
    void resume_after3min_accumulates120k() {
        svc.pause(user, 1L);
        trip.setWaitingStartedAt(LocalDateTime.now().minusSeconds(180)); // 3 daqiqa kutish
        Map<String, Object> r = svc.resume(user, 1L);
        // billable = 180-60 = 120s; cost = 120 * 60000 / 60 = 120000 tiyin
        assertEquals(120_000L, trip.getWaitingPrice());
        assertFalse((Boolean) r.get("paused"));
        assertNull(trip.getWaitingStartedAt());
        assertEquals(120_000L, ((Number) r.get("addedTiyin")).longValue());
    }

    @Test
    void twoPauses_accumulate() {
        svc.pause(user, 1L);
        trip.setWaitingStartedAt(LocalDateTime.now().minusSeconds(120)); // billable 60s -> 60000
        svc.resume(user, 1L);
        assertEquals(60_000L, trip.getWaitingPrice());
        svc.pause(user, 1L);
        trip.setWaitingStartedAt(LocalDateTime.now().minusSeconds(120)); // yana 60000 -> jami 120000
        svc.resume(user, 1L);
        assertEquals(120_000L, trip.getWaitingPrice());
    }

    @Test
    void shortPause_underFree_noCharge() {
        svc.pause(user, 1L);
        trip.setWaitingStartedAt(LocalDateTime.now().minusSeconds(30)); // < 60s free
        svc.resume(user, 1L);
        assertEquals(0L, trip.getWaitingPrice());
    }

    @Test
    void resume_whenNotPaused_noop() {
        Map<String, Object> r = svc.resume(user, 1L);
        assertEquals(0L, ((Number) r.get("addedTiyin")).longValue());
        assertEquals(0L, trip.getWaitingPrice());
    }
}
